package com.mandao.grc.modules.audit.management;

import com.mandao.grc.modules.audit.HashChainService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 审计计划业务服务（M3 审计管理）。
 *
 * 隔离：方法 @Transactional 且位于 com.mandao.grc.modules 包 → {@link com.mandao.grc.common.isolation.OrgScopeAspect}
 * 在事务内自动注入 app.visible_orgs，RLS 裁剪数据并校验写入（WITH CHECK，V3 已为 audit_plan 建）。
 *
 * 计划生命周期状态机：PLANNED → IN_PROGRESS → REPORTING → CLOSED；
 * 另允许 PLANNED/IN_PROGRESS → CANCELLED（取消，终态）。非法流转抛 {@link IllegalStateException}。
 *
 * 留痕：每次流转后 {@link HashChainService#append} 写入按 org 分链的防篡改哈希链（entity="AUDIT_PLAN:{id}"）。
 *
 * 调度兼容：新建计划写 external_status='PLANNED'，reminder_days 由 V3 库级 DEFAULT '{15,10}' 兜底，
 * 故 EXTERNAL 计划仍可被 ExpiryScanService 扫描产 EXT_AUDIT_PLAN_APPROACHING；本 status 不影响调度。
 *
 * 设计依据：需求文档 M3 审计管理（审计计划生命周期）、D2-5 编码规范。
 */
@Service
public class AuditPlanService {

    private final AuditPlanRepository repository;
    private final HashChainService hashChainService;

    public AuditPlanService(AuditPlanRepository repository, HashChainService hashChainService) {
        this.repository = repository;
        this.hashChainService = hashChainService;
    }

    /** 列出当前主体可见组织范围内的审计计划（无 org 过滤，靠切面 + RLS）。 */
    @Transactional(readOnly = true)
    public List<AuditPlan> list() {
        return repository.findAll();
    }

    /** 按审计类型列出（内部/外部/监管分视图）；type 为 null 返回全部。 */
    @Transactional(readOnly = true)
    public List<AuditPlan> listByType(AuditType type) {
        return type == null ? repository.findAll() : repository.findByAuditTypeOrderByIdDesc(type);
    }

    /** 按 id 取审计计划（仅能取到可见组织内的；不可见则视为不存在）。 */
    @Transactional(readOnly = true)
    public AuditPlan get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("审计计划不存在或不可见：id=" + id));
    }

    /**
     * 新建审计计划（PLANNED 态）。
     *
     * @param orgId         归属组织（须在 visible_orgs 内，否则 RLS WITH CHECK 拒绝写入）
     * @param auditType     审计类型（唯 EXTERNAL 的发现可走对外回函漏斗）
     * @param planStartDate 计划开始日（V3 NOT NULL；调度据此 + reminder_days 产临近提醒）
     */
    @Transactional
    public AuditPlan create(Long orgId, String title, AuditType auditType, LocalDate planStartDate, String actor) {
        AuditPlan p = new AuditPlan(orgId, title, auditType, planStartDate);
        AuditPlan saved = repository.save(p);
        appendLog(saved, "AUDIT_PLAN_CREATE", actor,
                "新建审计计划 title=" + title + " type=" + auditType + " start=" + planStartDate);
        return saved;
    }

    /** 开始审计：PLANNED → IN_PROGRESS。 */
    @Transactional
    public AuditPlan start(Long id, String actor) {
        AuditPlan p = get(id);
        transition(p, AuditPlanStatus.PLANNED, AuditPlanStatus.IN_PROGRESS);
        AuditPlan saved = repository.save(p);
        appendLog(saved, "AUDIT_PLAN_START", actor, "开始审计执行");
        return saved;
    }

    /** 出具报告：IN_PROGRESS → REPORTING。 */
    @Transactional
    public AuditPlan report(Long id, String actor) {
        AuditPlan p = get(id);
        transition(p, AuditPlanStatus.IN_PROGRESS, AuditPlanStatus.REPORTING);
        AuditPlan saved = repository.save(p);
        appendLog(saved, "AUDIT_PLAN_REPORT", actor, "出具审计报告");
        return saved;
    }

    /** 关闭审计：REPORTING → CLOSED（终态）。 */
    @Transactional
    public AuditPlan close(Long id, String actor) {
        AuditPlan p = get(id);
        transition(p, AuditPlanStatus.REPORTING, AuditPlanStatus.CLOSED);
        AuditPlan saved = repository.save(p);
        appendLog(saved, "AUDIT_PLAN_CLOSE", actor, "关闭审计计划");
        return saved;
    }

    /** 取消审计：PLANNED 或 IN_PROGRESS → CANCELLED（终态）。 */
    @Transactional
    public AuditPlan cancel(Long id, String actor) {
        AuditPlan p = get(id);
        AuditPlanStatus from = p.getStatus();
        if (from != AuditPlanStatus.PLANNED && from != AuditPlanStatus.IN_PROGRESS) {
            throw new IllegalStateException(
                    "非法状态流转：审计计划 id=" + p.getId()
                            + " 当前状态=" + from
                            + "，仅允许从 PLANNED / IN_PROGRESS 取消到 CANCELLED");
        }
        p.setStatus(AuditPlanStatus.CANCELLED);
        AuditPlan saved = repository.save(p);
        appendLog(saved, "AUDIT_PLAN_CANCEL", actor, "取消审计计划");
        return saved;
    }

    // ---------- 内部辅助 ----------

    /** 校验并执行一次合法流转：当前态须 == expectedFrom，否则视为非法流转抛异常。 */
    private void transition(AuditPlan p, AuditPlanStatus expectedFrom, AuditPlanStatus to) {
        if (p.getStatus() != expectedFrom) {
            throw new IllegalStateException(
                    "非法状态流转：审计计划 id=" + p.getId()
                            + " 当前状态=" + p.getStatus()
                            + "，仅允许从 " + expectedFrom + " 流转到 " + to);
        }
        p.setStatus(to);
    }

    /** 统一留痕入口：entity 统一格式 "AUDIT_PLAN:{id}"，便于审计按对象检索。 */
    private void appendLog(AuditPlan p, String action, String actor, String detail) {
        hashChainService.append(p.getOrgId(), action, actor, "AUDIT_PLAN:" + p.getId(), detail);
    }
}
