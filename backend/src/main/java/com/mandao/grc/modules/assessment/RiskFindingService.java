package com.mandao.grc.modules.assessment;

import com.mandao.grc.modules.audit.HashChainService;
import com.mandao.grc.modules.workflow.ApprovalDecision;
import com.mandao.grc.modules.workflow.WorkflowService;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 风险发现业务服务（M2 风险评估核心；落地 CR-002 残余风险关闭门控红线）。
 *
 * 隔离/留痕范式同 {@link AssessmentService}：方法 @Transactional → 切面自动注入 visible_orgs，
 * RLS 裁剪 + WITH CHECK 校验；每次流转/接受调用 {@link HashChainService#append} 留痕。
 *
 * 状态机：OPEN → IN_TREATMENT → DONE → VERIFIED。
 *
 * ===== 关闭门控（CR-002 红线，本类的核心约束）=====
 * 当 residual_level ∈ {HIGH, VERY_HIGH} 时，若该 finding 尚无有效风险接受（risk_acceptance_id 未回填），
 * 则【禁止】流转到 DONE / VERIFIED，抛 {@link RiskCloseGateException}；
 * 残余等级为低（VERY_LOW/LOW/MID）或已具备有效 acceptance 时方可放行。
 * 判定见 {@link #assertClosable}。
 *
 * 设计依据：D1-2（风险发现生命周期、关闭门控）、D1-6（表单引擎）、D1-3 §8、D2-5。
 */
@Service
public class RiskFindingService {

    /** 风险接受审批的业务类型与审批候选组（Flowable）。 */
    public static final String ACCEPT_BIZ_TYPE = "RISK_ACCEPTANCE";
    public static final String ACCEPT_APPROVER_GROUP = "RISK_ACCEPT_APPROVER";

    private final RiskFindingRepository findingRepository;
    private final RiskAcceptanceRepository acceptanceRepository;
    private final HashChainService hashChainService;
    private final WorkflowService workflowService;

    public RiskFindingService(RiskFindingRepository findingRepository,
                              RiskAcceptanceRepository acceptanceRepository,
                              HashChainService hashChainService,
                              WorkflowService workflowService) {
        this.findingRepository = findingRepository;
        this.acceptanceRepository = acceptanceRepository;
        this.hashChainService = hashChainService;
        this.workflowService = workflowService;
    }

    /** 列出某评估下的全部风险发现（受 RLS 裁剪）。 */
    @Transactional(readOnly = true)
    public List<RiskFinding> listByAssessment(Long assessmentId) {
        return findingRepository.findByAssessmentId(assessmentId);
    }

    /** 按 id 取风险发现（不可见则视为不存在）。 */
    @Transactional(readOnly = true)
    public RiskFinding get(Long id) {
        return findingRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("风险发现不存在或不可见：id=" + id));
    }

    /**
     * 新建风险发现（OPEN 态）。
     *
     * @param orgId        归属组织（须在 visible_orgs 内）
     * @param assessmentId 所属评估
     * @param inherentLevel 固有风险等级（五级）
     */
    @Transactional
    public RiskFinding createFinding(Long orgId, Long assessmentId, String title,
                                     RiskLevel inherentLevel, String actor) {
        RiskFinding f = new RiskFinding(orgId, assessmentId, title, inherentLevel);
        RiskFinding saved = findingRepository.save(f);
        appendLog(saved, "FINDING_CREATE", actor,
                "新建风险发现 title=" + title + " inherent=" + inherentLevel);
        return saved;
    }

    /**
     * 录入处置方案：OPEN → IN_TREATMENT（仅 OPEN 态可设置）。
     */
    @Transactional
    public RiskFinding setTreatment(Long id, String treatmentPlan, String actor) {
        return setTreatment(id, null, treatmentPlan, actor);
    }

    /** 录入处置（带四选一决策：MITIGATE 降低/ACCEPT 接受/TRANSFER 转移/AVOID 规避，需求 4.5.3）。 */
    @Transactional
    public RiskFinding setTreatment(Long id, String treatmentDecision, String treatmentPlan, String actor) {
        RiskFinding f = get(id);
        if (f.getStatus() != RiskFindingStatus.OPEN) {
            throw new IllegalStateException(
                    "仅 OPEN 态风险发现可录入处置方案，当前状态：" + f.getStatus());
        }
        f.setTreatmentPlan(treatmentPlan);
        f.setTreatmentDecision(treatmentDecision);
        f.setStatus(RiskFindingStatus.IN_TREATMENT);
        RiskFinding saved = findingRepository.save(f);
        appendLog(saved, "FINDING_TREAT", actor, "录入处置方案");
        return saved;
    }

    /**
     * 评估残余风险等级（可在 OPEN/IN_TREATMENT 态设置，不改变状态）。
     * 残余等级是关闭门控的判定依据，故单独留痕。
     */
    @Transactional
    public RiskFinding setResidual(Long id, RiskLevel residualLevel, String actor) {
        RiskFinding f = get(id);
        if (f.getStatus() == RiskFindingStatus.DONE || f.getStatus() == RiskFindingStatus.VERIFIED) {
            throw new IllegalStateException(
                    "已关闭(DONE/VERIFIED)的风险发现不可再改残余等级，当前状态：" + f.getStatus());
        }
        f.setResidualLevel(residualLevel);
        RiskFinding saved = findingRepository.save(f);
        appendLog(saved, "FINDING_RESIDUAL", actor, "评估残余风险等级=" + residualLevel);
        return saved;
    }

    /**
     * 申请风险接受（A2 审批化）：登记一条 PENDING 接受并启动 Flowable 审批流，【暂不放行】
     * ——不回填 finding.risk_acceptance_id，故 CR-002 门控对关闭仍生效。审批通过后才放行。
     *
     * 防重：已有有效（已通过）接受、或已有进行中的接受审批，均拒绝重复申请（亦避免 businessKey 冲突）。
     *
     * @param requester 申请人
     * @param reason    接受理由
     */
    @Transactional
    public RiskAcceptance requestAcceptance(Long findingId, String requester, String reason, String actor) {
        RiskFinding f = get(findingId);
        if (f.getRiskAcceptanceId() != null) {
            throw new IllegalStateException("风险发现 id=" + findingId + " 已有有效风险接受，无需重复申请");
        }
        if (workflowService.activeTask(ACCEPT_BIZ_TYPE, f.getId()) != null) {
            throw new IllegalStateException("风险发现 id=" + findingId + " 已有待审批的风险接受申请");
        }
        RiskAcceptance acceptance = new RiskAcceptance(f.getOrgId(), f.getId(), requester, reason); // PENDING
        RiskAcceptance saved = acceptanceRepository.save(acceptance);
        appendLog(f, "RISK_ACCEPT_REQUEST", actor,
                "申请风险接受 requester=" + requester + " acceptanceId=" + saved.getId());
        // 启动审批流（与申请登记同事务原子）。审批人按候选组 RISK_ACCEPT_APPROVER 派单。
        workflowService.submit(ACCEPT_BIZ_TYPE, f.getId(), f.getOrgId(), ACCEPT_APPROVER_GROUP, actor);
        return saved;
    }

    /**
     * 审批风险接受申请（A2 审批化核心，CR-002 放行侧）：
     * 定位该发现进行中的接受审批任务 → {@link WorkflowService#decide} 完成（推进流程 + 审批留痕）→
     * 通过则置 acceptance=APPROVED 并【回填 finding.risk_acceptance_id（门控解除）】；驳回则置 REJECTED、不放行。
     * 审批与放行回填【同事务原子】。
     *
     * @param decision 审批结论（通过/驳回）
     * @param approver 审批人（留痕 actor）
     * @param comment  审批意见（驳回原因，可空）
     */
    @Transactional
    public RiskAcceptance decideAcceptance(Long findingId, ApprovalDecision decision, String approver, String comment) {
        RiskFinding f = get(findingId);
        RiskAcceptance acceptance = acceptanceRepository
                .findFirstByFindingIdAndStatusOrderByIdDesc(f.getId(), AcceptanceStatus.PENDING)
                .orElseThrow(() -> new IllegalStateException("风险发现 id=" + findingId + " 无待审批的风险接受申请"));
        Task task = workflowService.activeTask(ACCEPT_BIZ_TYPE, f.getId());
        if (task == null) {
            throw new IllegalStateException("风险发现 id=" + findingId + " 无进行中的风险接受审批任务");
        }
        // 先完成审批任务，再据结论放行/驳回——同事务，一致提交/回滚。
        workflowService.decide(task.getId(), decision, approver, comment);
        if (decision == ApprovalDecision.APPROVED) {
            acceptance.approve(approver);
            f.setRiskAcceptanceId(acceptance.getId()); // 回填放行凭据 → CR-002 门控解除
            findingRepository.save(f);
            appendLog(f, "RISK_ACCEPT_APPROVE", approver,
                    "风险接受通过，放行凭据 acceptanceId=" + acceptance.getId());
        } else {
            acceptance.reject(approver);
            appendLog(f, "RISK_ACCEPT_REJECT", approver,
                    "风险接受驳回：" + (comment == null ? "" : comment));
        }
        return acceptanceRepository.save(acceptance);
    }

    /**
     * 关闭：OPEN/IN_TREATMENT → DONE，或 DONE → VERIFIED。
     *
     * 【关闭门控 CR-002 红线】流转到 DONE/VERIFIED 前，先 {@link #assertClosable} 校验：
     * 高残余（HIGH/VERY_HIGH）且无有效 acceptance → 抛 {@link RiskCloseGateException}。
     *
     * @param verify false=关闭到 DONE；true=验证到 VERIFIED（要求当前为 DONE）
     */
    @Transactional
    public RiskFinding close(Long id, boolean verify, String actor) {
        RiskFinding f = get(id);

        // —— 关闭门控：进入 DONE/VERIFIED 前一律先过门控（红线核心）——
        assertClosable(f);

        if (verify) {
            // DONE → VERIFIED
            transition(f, RiskFindingStatus.DONE, RiskFindingStatus.VERIFIED);
        } else {
            // OPEN/IN_TREATMENT → DONE
            if (f.getStatus() != RiskFindingStatus.OPEN && f.getStatus() != RiskFindingStatus.IN_TREATMENT) {
                throw new IllegalStateException(
                        "仅 OPEN/IN_TREATMENT 态可关闭到 DONE，当前状态：" + f.getStatus());
            }
            f.setStatus(RiskFindingStatus.DONE);
        }
        RiskFinding saved = findingRepository.save(f);
        appendLog(saved, verify ? "FINDING_VERIFY" : "FINDING_CLOSE", actor,
                "关闭流转 → " + saved.getStatus()
                        + "（残余=" + saved.getResidualLevel()
                        + "，acceptanceId=" + saved.getRiskAcceptanceId() + "）");
        return saved;
    }

    // ---------- 内部辅助 ----------

    /**
     * 关闭门控判定（CR-002 红线）：
     * residual_level ∈ {HIGH, VERY_HIGH} 时，必须已具备有效风险接受（risk_acceptance_id 已回填），
     * 否则禁止关闭——抛 {@link RiskCloseGateException}。
     * 低残余（含未评估残余）不受此门控约束。
     */
    private void assertClosable(RiskFinding f) {
        RiskLevel residual = f.getResidualLevel();
        if (residual != null && residual.isHighResidual() && f.getRiskAcceptanceId() == null) {
            throw new RiskCloseGateException(
                    "残余风险为 " + residual + " 的风险发现(id=" + f.getId()
                            + ")在无有效风险接受(risk_acceptance)的情况下禁止关闭（CR-002 残余风险关闭门控）。");
        }
    }

    /** 校验并执行一次合法流转：当前态须 == expectedFrom，否则抛异常。 */
    private void transition(RiskFinding f, RiskFindingStatus expectedFrom, RiskFindingStatus to) {
        if (f.getStatus() != expectedFrom) {
            throw new IllegalStateException(
                    "非法状态流转：风险发现 id=" + f.getId()
                            + " 当前状态=" + f.getStatus()
                            + "，仅允许从 " + expectedFrom + " 流转到 " + to);
        }
        f.setStatus(to);
    }

    /** 统一留痕入口：entity 统一格式 "FINDING:{id}"。 */
    private void appendLog(RiskFinding f, String action, String actor, String detail) {
        hashChainService.append(f.getOrgId(), action, actor, "FINDING:" + f.getId(), detail);
    }
}
