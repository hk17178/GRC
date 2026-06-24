package com.mandao.grc.modules.policy;

import com.mandao.grc.modules.audit.HashChainService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 制度业务服务（M1 制度体系）。
 *
 * 隔离：本服务【不手动注入隔离上下文，也不手写 org 过滤】——只要方法带 @Transactional
 * 且位于 com.mandao.grc.modules 包，{@link com.mandao.grc.common.isolation.OrgScopeAspect}
 * 会在事务内自动注入 app.visible_orgs，随后 RLS 裁剪数据并校验写入（WITH CHECK）。
 *
 * 状态机：DRAFT → REVIEW → EFFECTIVE → DEPRECATED；
 * REVIEW 可被驳回回 DRAFT。非法流转一律抛 {@link IllegalStateException}。
 *
 * 留痕：每次状态流转 / 签署后，调用 {@link HashChainService#append} 写入按 org 分链的
 * 防篡改哈希链（D1-3 §8 ADR-C）。HashChainService 自身 @Transactional，与本服务同事务/同连接，
 * 共享同一 visible_orgs 注入，故留痕与业务在同一组织范围内一致提交或一起回滚。
 *
 * 设计依据：D1-2 制度生命周期、D1-3 §5.1/§8、D2-5 编码规范。
 */
@Service
public class PolicyService {

    private final PolicyRepository policyRepository;
    private final PolicySignoffRepository signoffRepository;
    private final HashChainService hashChainService;

    public PolicyService(PolicyRepository policyRepository,
                         PolicySignoffRepository signoffRepository,
                         HashChainService hashChainService) {
        this.policyRepository = policyRepository;
        this.signoffRepository = signoffRepository;
        this.hashChainService = hashChainService;
    }

    /**
     * 列出当前主体可见组织范围内的全部制度。
     * findAll() 不带任何 org 过滤——隔离完全由切面注入 + RLS 兜底保证。
     */
    @Transactional(readOnly = true)
    public List<Policy> list() {
        return policyRepository.findAll();
    }

    /** 按 id 取制度（仅能取到可见组织内的；不可见则视为不存在）。 */
    @Transactional(readOnly = true)
    public Policy get(Long id) {
        return policyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("制度不存在或不可见：id=" + id));
    }

    /**
     * 新建草稿制度。
     *
     * @param orgId  归属组织（必须在当前 visible_orgs 内，否则 RLS WITH CHECK 拒绝写入）
     * @param actor  操作人（用于留痕）
     */
    @Transactional
    public Policy create(Long orgId, String code, String title, String content, String actor) {
        Policy policy = new Policy(orgId, code, title, content);
        Policy saved = policyRepository.save(policy);
        appendLog(saved, "POLICY_CREATE", actor, "新建制度草稿 code=" + code);
        return saved;
    }

    /**
     * 提交评审：DRAFT → REVIEW。
     * 非 DRAFT 态调用属非法流转，抛 {@link IllegalStateException}。
     */
    @Transactional
    public Policy submitForApproval(Long id, String actor) {
        Policy policy = get(id);
        transition(policy, PolicyStatus.DRAFT, PolicyStatus.REVIEW);
        Policy saved = policyRepository.save(policy);
        appendLog(saved, "POLICY_SUBMIT", actor, "提交评审");
        return saved;
    }

    /**
     * 审批通过：REVIEW → EFFECTIVE。
     */
    @Transactional
    public Policy approve(Long id, String actor) {
        Policy policy = get(id);
        transition(policy, PolicyStatus.REVIEW, PolicyStatus.EFFECTIVE);
        Policy saved = policyRepository.save(policy);
        appendLog(saved, "POLICY_APPROVE", actor, "审批通过并生效");
        return saved;
    }

    /**
     * 审批驳回：REVIEW → DRAFT（退回起草人修改）。
     */
    @Transactional
    public Policy reject(Long id, String actor, String reason) {
        Policy policy = get(id);
        transition(policy, PolicyStatus.REVIEW, PolicyStatus.DRAFT);
        Policy saved = policyRepository.save(policy);
        appendLog(saved, "POLICY_REJECT", actor, "审批驳回，原因：" + (reason == null ? "" : reason));
        return saved;
    }

    /**
     * 废止：EFFECTIVE → DEPRECATED（终态）。
     */
    @Transactional
    public Policy archive(Long id, String actor) {
        Policy policy = get(id);
        transition(policy, PolicyStatus.EFFECTIVE, PolicyStatus.DEPRECATED);
        Policy saved = policyRepository.save(policy);
        appendLog(saved, "POLICY_ARCHIVE", actor, "废止");
        return saved;
    }

    /**
     * 签署确认：仅 EFFECTIVE 态的制度可被签署。
     *
     * 幂等/重复保护：同一制度同一签署人重复签署，由表上 UNIQUE(policy_id, signer) 在落库时拒绝
     * （抛出 DataIntegrityViolationException），事务回滚。
     */
    @Transactional
    public PolicySignoff signoff(Long policyId, String signer) {
        Policy policy = get(policyId);
        if (policy.getStatus() != PolicyStatus.EFFECTIVE) {
            throw new IllegalStateException(
                    "仅已生效(EFFECTIVE)制度可签署，当前状态：" + policy.getStatus());
        }
        PolicySignoff signoff = new PolicySignoff(policy.getId(), policy.getOrgId(), signer);
        PolicySignoff saved = signoffRepository.save(signoff);
        // 签署本身也是业务关键操作，入链留痕。
        hashChainService.append(policy.getOrgId(), "POLICY_SIGNOFF", signer,
                "POLICY:" + policy.getId(), "签署确认 signer=" + signer);
        return saved;
    }

    // ---------- 内部辅助 ----------

    /**
     * 校验并执行一次合法流转：要求当前态 == 期望的 from，否则视为非法流转抛异常。
     * 将"校验 + 推进"收敛到一处，避免各方法重复判断。
     */
    private void transition(Policy policy, PolicyStatus expectedFrom, PolicyStatus to) {
        if (policy.getStatus() != expectedFrom) {
            throw new IllegalStateException(
                    "非法状态流转：制度 id=" + policy.getId()
                            + " 当前状态=" + policy.getStatus()
                            + "，仅允许从 " + expectedFrom + " 流转到 " + to);
        }
        policy.setStatus(to);
    }

    /**
     * 统一的留痕入口：把一次制度操作追加进该 org 的防篡改哈希链。
     * entity 统一格式 "POLICY:{id}"，便于审计按对象检索。
     */
    private void appendLog(Policy policy, String action, String actor, String detail) {
        hashChainService.append(policy.getOrgId(), action, actor,
                "POLICY:" + policy.getId(), detail);
    }
}
