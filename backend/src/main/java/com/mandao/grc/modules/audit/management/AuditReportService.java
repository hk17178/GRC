package com.mandao.grc.modules.audit.management;

import com.mandao.grc.modules.audit.HashChainService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 审计报告服务（V47 · A1）：自动组稿 + 生命周期 DRAFT → COMMENTING → FINAL → ISSUED。
 *
 * 自动组稿：按 计划信息 + 发现五要素（现状/标准/原因/影响/建议 + 管理层回应）+ 整改台账
 * 生成结构化正文草稿；草稿/征求意见阶段可编辑，定稿须选审计意见，签发后冻结并写哈希链。
 *
 * 隔离：方法 @Transactional 且位于 modules 包 → 切面注入 visible_orgs，RLS 裁剪。
 */
@Service
public class AuditReportService {

    private static final Map<AuditOpinion, String> OPINION_LABEL = Map.of(
            AuditOpinion.SATISFACTORY, "满意",
            AuditOpinion.GENERALLY_SATISFACTORY, "基本满意",
            AuditOpinion.NEEDS_IMPROVEMENT, "需改进",
            AuditOpinion.UNSATISFACTORY, "不满意");

    private static final Map<AuditSeverity, String> SEV_LABEL = Map.of(
            AuditSeverity.VERY_LOW, "极低", AuditSeverity.LOW, "低",
            AuditSeverity.MID, "中", AuditSeverity.HIGH, "高");

    private final AuditReportRepository reportRepo;
    private final AuditPlanRepository planRepo;
    private final AuditFindingRepository findingRepo;
    private final RemediationOrderRepository remediationRepo;
    private final HashChainService hashChainService;

    public AuditReportService(AuditReportRepository reportRepo, AuditPlanRepository planRepo,
                              AuditFindingRepository findingRepo, RemediationOrderRepository remediationRepo,
                              HashChainService hashChainService) {
        this.reportRepo = reportRepo;
        this.planRepo = planRepo;
        this.findingRepo = findingRepo;
        this.remediationRepo = remediationRepo;
        this.hashChainService = hashChainService;
    }

    @Transactional(readOnly = true)
    public AuditReport byPlan(Long planId) {
        return reportRepo.findByPlanId(planId).orElse(null);
    }

    /**
     * 生成报告草稿（自动组稿）。幂等：该计划已有报告则直接返回既有。
     */
    @Transactional
    public AuditReport createDraft(Long planId, String actor) {
        AuditReport existing = reportRepo.findByPlanId(planId).orElse(null);
        if (existing != null) {
            return existing;
        }
        AuditPlan plan = planRepo.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("审计计划不存在或不可见：id=" + planId));
        List<AuditFinding> findings = findingRepo.findAll().stream()
                .filter(f -> planId.equals(f.getAuditPlanId())).toList();
        List<RemediationOrder> remediations = remediationRepo.findAll().stream()
                .filter(r -> findings.stream().anyMatch(f -> f.getId().equals(r.getFindingId()))).toList();

        AuditReport saved = reportRepo.save(new AuditReport(plan.getOrgId(), planId,
                plan.getTitle() + " 审计报告", null, compose(plan, findings, remediations), actor));
        hashChainService.append(plan.getOrgId(), "AUDIT_REPORT_DRAFT", actor,
                "AUDIT_REPORT:" + saved.getId(), "生成审计报告草稿（自动组稿）plan=" + planId);
        return saved;
    }

    /** 编辑报告（DRAFT/COMMENTING 可改；定稿后冻结）。 */
    @Transactional
    public AuditReport update(Long id, String title, AuditOpinion opinion, String summary, String content, String actor) {
        AuditReport r = get(id);
        if (!"DRAFT".equals(r.getStatus()) && !"COMMENTING".equals(r.getStatus())) {
            throw new IllegalStateException("报告已定稿/签发，不可修改");
        }
        r.applyEdit(title, opinion, summary, content);
        AuditReport saved = reportRepo.save(r);
        hashChainService.append(r.getOrgId(), "AUDIT_REPORT_EDIT", actor, "AUDIT_REPORT:" + id, "编辑审计报告");
        return saved;
    }

    /** 征求意见：DRAFT → COMMENTING（发被审计单位征求意见）。 */
    @Transactional
    public AuditReport submitComment(Long id, String actor) {
        AuditReport r = transition(id, "DRAFT", "COMMENTING");
        hashChainService.append(r.getOrgId(), "AUDIT_REPORT_COMMENT", actor, "AUDIT_REPORT:" + id, "报告征求意见");
        return r;
    }

    /** 定稿：COMMENTING → FINAL。定稿必须已选审计意见。 */
    @Transactional
    public AuditReport finalizeReport(Long id, String actor) {
        AuditReport r = get(id);
        if (r.getOpinion() == null) {
            throw new IllegalStateException("定稿前须先选定审计意见（四级）");
        }
        transition(r, "COMMENTING", "FINAL");
        AuditReport saved = reportRepo.save(r);
        hashChainService.append(r.getOrgId(), "AUDIT_REPORT_FINAL", actor, "AUDIT_REPORT:" + id,
                "报告定稿 · 审计意见=" + OPINION_LABEL.get(r.getOpinion()));
        return saved;
    }

    /** 签发：FINAL → ISSUED（终态，落签发人/时间）。 */
    @Transactional
    public AuditReport issue(Long id, String actor) {
        AuditReport r = get(id);
        transition(r, "FINAL", "ISSUED");
        r.issue(actor);
        AuditReport saved = reportRepo.save(r);
        hashChainService.append(r.getOrgId(), "AUDIT_REPORT_ISSUE", actor, "AUDIT_REPORT:" + id, "报告签发");
        return saved;
    }

    // ---------- 内部辅助 ----------

    private AuditReport get(Long id) {
        return reportRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("审计报告不存在或不可见：id=" + id));
    }

    private AuditReport transition(Long id, String from, String to) {
        AuditReport r = get(id);
        transition(r, from, to);
        return reportRepo.save(r);
    }

    private void transition(AuditReport r, String from, String to) {
        if (!from.equals(r.getStatus())) {
            throw new IllegalStateException("非法状态流转：报告当前=" + r.getStatus() + "，仅允许 " + from + " → " + to);
        }
        r.setStatus(to);
    }

    /** 自动组稿：计划信息 + 发现五要素 + 管理层回应 + 整改台账 → 结构化正文。 */
    private static String compose(AuditPlan plan, List<AuditFinding> findings, List<RemediationOrder> remediations) {
        StringBuilder sb = new StringBuilder();
        sb.append("一、审计概况\n")
                .append("审计项目：").append(plan.getTitle())
                .append("（AP-").append(plan.getId()).append("，").append(plan.getAuditType()).append("）\n")
                .append("计划开始：").append(plan.getPlanStartDate()).append("\n\n");

        sb.append("二、审计发现（").append(findings.size()).append(" 项）\n");
        int i = 1;
        for (AuditFinding f : findings) {
            sb.append(i++).append(". ").append(nvl(f.getTitle()))
                    .append("【严重度：").append(SEV_LABEL.getOrDefault(f.getSeverity(), String.valueOf(f.getSeverity()))).append("】\n")
                    .append("   现状：").append(nvl(f.getConditionDesc())).append("\n")
                    .append("   标准：").append(nvl(f.getCriteriaDesc())).append("\n")
                    .append("   原因：").append(nvl(f.getCause())).append("\n")
                    .append("   影响：").append(nvl(f.getEffect())).append("\n")
                    .append("   建议：").append(nvl(f.getRecommendation())).append("\n")
                    .append("   管理层回应：").append(nvl(f.getMgmtResponse())).append("\n");
        }
        if (findings.isEmpty()) {
            sb.append("（无）\n");
        }

        sb.append("\n三、整改安排（").append(remediations.size()).append(" 项）\n");
        for (RemediationOrder r : remediations) {
            sb.append("RO-").append(r.getId()).append("（发现 AF-").append(r.getFindingId()).append("）：")
                    .append(nvl(r.getMeasure()))
                    .append(" · 责任人 ").append(nvl(r.getAssignee()))
                    .append(" · 期限 ").append(r.getDueDate() == null ? "—" : r.getDueDate())
                    .append(" · 状态 ").append(r.getStatus()).append("\n");
        }
        if (remediations.isEmpty()) {
            sb.append("（无）\n");
        }
        sb.append("\n四、总体评价与审计意见\n（定稿前补充总体评价，并在报告属性中选定审计意见分级。）\n");
        return sb.toString();
    }

    private static String nvl(String s) {
        return s == null || s.isBlank() ? "（待补充）" : s;
    }
}
