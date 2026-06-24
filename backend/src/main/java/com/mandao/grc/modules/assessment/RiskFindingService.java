package com.mandao.grc.modules.assessment;

import com.mandao.grc.modules.audit.HashChainService;
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

    private final RiskFindingRepository findingRepository;
    private final RiskAcceptanceRepository acceptanceRepository;
    private final HashChainService hashChainService;

    public RiskFindingService(RiskFindingRepository findingRepository,
                              RiskAcceptanceRepository acceptanceRepository,
                              HashChainService hashChainService) {
        this.findingRepository = findingRepository;
        this.acceptanceRepository = acceptanceRepository;
        this.hashChainService = hashChainService;
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
        RiskFinding f = get(id);
        if (f.getStatus() != RiskFindingStatus.OPEN) {
            throw new IllegalStateException(
                    "仅 OPEN 态风险发现可录入处置方案，当前状态：" + f.getStatus());
        }
        f.setTreatmentPlan(treatmentPlan);
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
     * 接受风险：登记一条 risk_acceptance 并回填 finding.risk_acceptance_id。
     * 这是高残余风险得以关闭的放行凭据（CR-002 门控的"放行"侧）。
     *
     * @param approver 接受审批人
     * @param reason   接受理由
     */
    @Transactional
    public RiskAcceptance accept(Long findingId, String approver, String reason, String actor) {
        RiskFinding f = get(findingId);
        RiskAcceptance acceptance = new RiskAcceptance(f.getOrgId(), f.getId(), approver, reason);
        RiskAcceptance saved = acceptanceRepository.save(acceptance);
        // 回填放行凭据
        f.setRiskAcceptanceId(saved.getId());
        findingRepository.save(f);
        hashChainService.append(f.getOrgId(), "FINDING_ACCEPT", actor,
                "FINDING:" + f.getId(),
                "接受风险 approver=" + approver + " acceptanceId=" + saved.getId());
        return saved;
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
