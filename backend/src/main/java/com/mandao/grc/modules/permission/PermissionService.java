package com.mandao.grc.modules.permission;

import com.mandao.grc.modules.audit.HashChainService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 权限授予/回收业务服务（M8 权限审批核心；落地"SoD 职责分离红线"）。
 *
 * 隔离：方法 @Transactional 且位于 com.mandao.grc.modules 包 → {@link com.mandao.grc.common.isolation.OrgScopeAspect}
 * 在事务内自动注入 app.visible_orgs，RLS 裁剪数据并校验写入（WITH CHECK，V7 已为 user_role_org/sod_exception 建）。
 *
 * 留痕：每次授权/回收/豁免后 {@link HashChainService#append} 写入按 org 分链的防篡改哈希链。
 *
 * ===== SoD 职责分离红线（本类核心约束）=====
 * 授权 {@link #grantRole} 时：若被授权 user 在该 org 已持有与目标角色互斥（{@link SodRule}）的【有效】角色
 *（active=true），且不存在针对该互斥规则的有效 {@link SodException} 豁免，则抛 {@link SodViolationException} 拒绝；
 * 若已补豁免则放行。回收 {@link #revokeRole} 置 active=false（软删，保留可追溯）。
 *
 * 设计依据：需求文档 M8 权限审批（RBAC/权限四元组/SoD）、D1-3 §4.7、D2-5。
 */
@Service
public class PermissionService {

    private final UserRoleOrgRepository userRoleOrgRepository;
    private final SodRuleRepository sodRuleRepository;
    private final SodExceptionRepository sodExceptionRepository;
    private final HashChainService hashChainService;

    public PermissionService(UserRoleOrgRepository userRoleOrgRepository,
                             SodRuleRepository sodRuleRepository,
                             SodExceptionRepository sodExceptionRepository,
                             HashChainService hashChainService) {
        this.userRoleOrgRepository = userRoleOrgRepository;
        this.sodRuleRepository = sodRuleRepository;
        this.sodExceptionRepository = sodExceptionRepository;
        this.hashChainService = hashChainService;
    }

    /** 列出某 org 下某 user 的全部授权行（含已回收，受 RLS 裁剪）。 */
    @Transactional(readOnly = true)
    public List<UserRoleOrg> listUserRoles(Long orgId, Long userId) {
        return userRoleOrgRepository.findByOrgIdAndUserId(orgId, userId);
    }

    /**
     * 授予角色（权限四元组 org × user × role × active=true）。
     *
     * SoD 红线：先检查目标角色与该 user 在该 org 现有【有效】角色是否互斥；互斥且无有效豁免 → 抛
     * {@link SodViolationException}。幂等：若已存在该四元组，active=false 则复活，active=true 则原样返回。
     *
     * @param orgId     归属组织（须在 visible_orgs 内，否则 RLS WITH CHECK 拒绝写入）
     * @param userId    被授权用户（app_user.id）
     * @param roleId    目标角色（role.id）
     * @param grantedBy 授权人 actor
     * @throws SodViolationException 违反 SoD 互斥红线且无有效豁免
     */
    @Transactional
    public UserRoleOrg grantRole(Long orgId, Long userId, Long roleId, String grantedBy) {
        // 1) SoD 红线校验
        enforceSod(orgId, userId, roleId);

        // 2) 幂等落地四元组
        UserRoleOrg existing = userRoleOrgRepository
                .findByOrgIdAndUserIdAndRoleId(orgId, userId, roleId)
                .orElse(null);
        UserRoleOrg saved;
        if (existing == null) {
            saved = userRoleOrgRepository.save(new UserRoleOrg(orgId, userId, roleId, grantedBy));
        } else if (!existing.isActive()) {
            existing.setActive(true);
            existing.setGrantedBy(grantedBy);
            saved = userRoleOrgRepository.save(existing);
        } else {
            saved = existing;  // 已有效，幂等返回
        }

        appendLog(orgId, "PERMISSION_GRANT", grantedBy, "USER_ROLE_ORG:" + saved.getId(),
                "授予角色 user=" + userId + " role=" + roleId + " org=" + orgId);
        return saved;
    }

    /** 回收角色：置 active=false（软删，保留可追溯）。不存在或已回收则幂等。 */
    @Transactional
    public UserRoleOrg revokeRole(Long orgId, Long userId, Long roleId, String actor) {
        UserRoleOrg uro = userRoleOrgRepository
                .findByOrgIdAndUserIdAndRoleId(orgId, userId, roleId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "授权不存在或不可见：org=" + orgId + " user=" + userId + " role=" + roleId));
        if (uro.isActive()) {
            uro.setActive(false);
            userRoleOrgRepository.save(uro);
        }
        appendLog(orgId, "PERMISSION_REVOKE", actor, "USER_ROLE_ORG:" + uro.getId(),
                "回收角色 user=" + userId + " role=" + roleId + " org=" + orgId);
        return uro;
    }

    /**
     * 登记一条 SoD 豁免（经审批），使原本被互斥红线拦截的授权得以放行。
     *
     * @param orgId     归属组织
     * @param userId    被豁免用户
     * @param sodRuleId 所豁免的互斥规则
     * @param approver  审批人
     * @param reason    豁免理由
     */
    @Transactional
    public SodException grantSodException(Long orgId, Long userId, Long sodRuleId,
                                          String approver, String reason) {
        SodException saved = sodExceptionRepository.save(
                new SodException(orgId, userId, sodRuleId, approver, reason));
        appendLog(orgId, "SOD_EXCEPTION_GRANT", approver, "SOD_EXCEPTION:" + saved.getId(),
                "登记 SoD 豁免 user=" + userId + " rule=" + sodRuleId + " reason=" + reason);
        return saved;
    }

    // ---------- 内部辅助 ----------

    /**
     * SoD 红线核心：若目标角色与该 user 在该 org 现有【有效】角色互斥，且无有效豁免，则抛异常。
     *
     * 实现：取涉及目标角色的全部互斥规则 → 对每条规则求互斥对手角色 → 若 user 在该 org 持有该对手角色（active）
     * 且对该规则无有效豁免 → 拒绝。
     */
    private void enforceSod(Long orgId, Long userId, Long roleId) {
        List<SodRule> rules = sodRuleRepository.findByRoleAIdOrRoleBId(roleId, roleId);
        if (rules.isEmpty()) {
            return;  // 目标角色不涉及任何互斥规则
        }
        List<UserRoleOrg> held = userRoleOrgRepository.findByOrgIdAndUserIdAndActiveTrue(orgId, userId);
        for (SodRule rule : rules) {
            Long counterpart = rule.counterpartOf(roleId);
            if (counterpart == null) {
                continue;
            }
            boolean holdsCounterpart = held.stream()
                    .anyMatch(h -> h.getRoleId().equals(counterpart));
            if (!holdsCounterpart) {
                continue;  // 未持有互斥对手角色，不触发
            }
            // 持有互斥角色 → 必须有针对本规则的有效豁免才放行
            boolean exempted = !sodExceptionRepository
                    .findByOrgIdAndUserIdAndSodRuleId(orgId, userId, rule.getId())
                    .isEmpty();
            if (!exempted) {
                throw new SodViolationException(
                        "SoD 职责分离红线拦截：user=" + userId + " 在 org=" + orgId
                                + " 已持有与目标角色(role=" + roleId + ")互斥的角色(role=" + counterpart
                                + ")，互斥规则 sodRule=" + rule.getId()
                                + "（" + rule.getDescription() + "），且无有效豁免，授权被拒。");
            }
        }
    }

    /** 统一留痕入口。 */
    private void appendLog(Long orgId, String action, String actor, String entity, String detail) {
        hashChainService.append(orgId, action, actor, entity, detail);
    }
}
