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
    private final AuditReportTemplateRepository templateRepo;
    private final HashChainService hashChainService;

    public AuditReportService(AuditReportRepository reportRepo, AuditPlanRepository planRepo,
                              AuditFindingRepository findingRepo, RemediationOrderRepository remediationRepo,
                              AuditReportTemplateRepository templateRepo,
                              HashChainService hashChainService) {
        this.reportRepo = reportRepo;
        this.planRepo = planRepo;
        this.findingRepo = findingRepo;
        this.remediationRepo = remediationRepo;
        this.templateRepo = templateRepo;
        this.hashChainService = hashChainService;
    }

    @Transactional(readOnly = true)
    public AuditReport byPlan(Long planId) {
        return reportRepo.findByPlanId(planId).orElse(null);
    }

    /**
     * 生成报告草稿（自动组稿）。幂等：该计划已有报告则直接返回既有。
     *
     * @param templateId 报告模板（V54，可空）：选模板则以模板正文为骨架，系统组稿作为附录随后。
     */
    @Transactional
    public AuditReport createDraft(Long planId, Long templateId, String actor) {
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

        String content = compose(plan, findings, remediations);
        if (templateId != null) {
            AuditReportTemplate tpl = templateRepo.findById(templateId)
                    .orElseThrow(() -> new IllegalArgumentException("报告模板不存在或不可见：id=" + templateId));
            if (!tpl.isEnabled()) {
                throw new IllegalStateException("报告模板已停用：" + tpl.getName());
            }
            content = tpl.getContent()
                    + "\n\n===== 系统组稿附录（计划 / 发现五要素 / 整改台账，供整理并入正文）=====\n\n"
                    + content;
        }
        AuditReport saved = reportRepo.save(new AuditReport(plan.getOrgId(), planId,
                plan.getTitle() + " 审计报告", null, content, actor));
        hashChainService.append(plan.getOrgId(), "AUDIT_REPORT_DRAFT", actor,
                "AUDIT_REPORT:" + saved.getId(), "生成审计报告草稿（自动组稿"
                        + (templateId == null ? "" : "，模板#" + templateId) + "）plan=" + planId);
        return saved;
    }

    /** 兼容旧签名：不使用模板。 */
    @Transactional
    public AuditReport createDraft(Long planId, String actor) {
        return createDraft(planId, null, actor);
    }

    // ---------- 报告模板管理（V54） ----------

    @Transactional(readOnly = true)
    public List<AuditReportTemplate> listTemplates() {
        return templateRepo.findAllByOrderByIdAsc();
    }

    @Transactional
    public AuditReportTemplate createTemplate(Long orgId, String name, String category, String content, String actor) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("模板正文不能为空");
        }
        AuditReportTemplate saved = templateRepo.save(new AuditReportTemplate(orgId, name, category, content, actor));
        hashChainService.append(orgId, "AUDIT_RPT_TPL_CREATE", actor, "AUDIT_RPT_TPL:" + saved.getId(),
                "新建审计报告模板「" + name + "」");
        return saved;
    }

    @Transactional
    public AuditReportTemplate updateTemplate(Long id, String name, String category, String content, String actor) {
        AuditReportTemplate t = templateRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("报告模板不存在或不可见：id=" + id));
        t.applyEdit(name, category, content);
        AuditReportTemplate saved = templateRepo.save(t);
        hashChainService.append(t.getOrgId(), "AUDIT_RPT_TPL_EDIT", actor, "AUDIT_RPT_TPL:" + id, "编辑报告模板");
        return saved;
    }

    @Transactional
    public AuditReportTemplate setTemplateEnabled(Long id, boolean enabled) {
        AuditReportTemplate t = templateRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("报告模板不存在或不可见：id=" + id));
        t.setEnabled(enabled);
        return templateRepo.save(t);
    }

    @Transactional
    public void deleteTemplate(Long id, String actor) {
        AuditReportTemplate t = templateRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("报告模板不存在或不可见：id=" + id));
        templateRepo.delete(t);
        hashChainService.append(t.getOrgId(), "AUDIT_RPT_TPL_DELETE", actor, "AUDIT_RPT_TPL:" + id,
                "删除报告模板「" + t.getName() + "」");
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

    // ---------- 文书套打（V52 · A3：通知书 / 报告 .docx） ----------

    /** 审计报告导出 .docx（标题/意见/总体评价/正文成文）。 */
    @Transactional(readOnly = true)
    public byte[] buildReportDocx(Long reportId) {
        AuditReport r = get(reportId);
        try (org.apache.poi.xwpf.usermodel.XWPFDocument doc = new org.apache.poi.xwpf.usermodel.XWPFDocument();
             java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            var title = doc.createParagraph();
            title.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
            var tr = title.createRun();
            tr.setText(r.getTitle());
            tr.setBold(true);
            tr.setFontSize(18);

            para(doc, "报告编号：AR-" + r.getId() + "　状态：" + r.getStatus()
                    + (r.getIssuedBy() == null ? "" : "　签发：" + r.getIssuedBy() + " " + r.getIssuedAt()));
            para(doc, "审计意见：" + (r.getOpinion() == null ? "（未定）" : OPINION_LABEL.get(r.getOpinion())));
            heading(doc, "总体评价");
            para(doc, r.getSummary() == null ? "—" : r.getSummary());
            heading(doc, "报告正文");
            for (String line : (r.getContent() == null ? "" : r.getContent()).split("\n")) {
                para(doc, line);
            }
            doc.write(out);
            return out.toByteArray();
        } catch (java.io.IOException ex) {
            throw new java.io.UncheckedIOException("报告导出失败", ex);
        }
    }

    /** 审计通知书导出 .docx（致被审计单位的正式文书结构）。 */
    @Transactional(readOnly = true)
    public byte[] buildNoticeDocx(Long planId) {
        AuditPlan p = planRepo.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("审计计划不存在或不可见：id=" + planId));
        try (org.apache.poi.xwpf.usermodel.XWPFDocument doc = new org.apache.poi.xwpf.usermodel.XWPFDocument();
             java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            var title = doc.createParagraph();
            title.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
            var tr = title.createRun();
            tr.setText("审 计 通 知 书");
            tr.setBold(true);
            tr.setFontSize(20);

            para(doc, "编号：AN-" + p.getId());
            para(doc, (p.getAuditee() == null ? "（被审计单位）" : p.getAuditee()) + "：");
            para(doc, "　　根据" + (p.getNoticeBasis() == null ? "年度审计安排" : p.getNoticeBasis())
                    + "，我部将对你单位开展「" + p.getTitle() + "」，现将有关事项通知如下：");
            heading(doc, "一、审计范围");
            para(doc, p.getNoticeScope() == null ? "—" : p.getNoticeScope());
            heading(doc, "二、审计时间");
            para(doc, "计划开始日：" + p.getPlanStartDate() + "，具体安排以现场沟通为准。");
            heading(doc, "三、审计组组成");
            para(doc, p.getAuditTeam() == null ? "—" : p.getAuditTeam());
            heading(doc, "四、配合要求");
            para(doc, "请你单位据实提供相关制度、记录与系统权限，并指定接口人配合审计工作。");
            para(doc, "");
            para(doc, "签发：" + (p.getNoticeIssuedBy() == null ? "（未签发）" : p.getNoticeIssuedBy())
                    + (p.getNoticeIssuedAt() == null ? "" : "　" + p.getNoticeIssuedAt().toLocalDate()));
            doc.write(out);
            return out.toByteArray();
        } catch (java.io.IOException ex) {
            throw new java.io.UncheckedIOException("通知书导出失败", ex);
        }
    }

    private static void para(org.apache.poi.xwpf.usermodel.XWPFDocument doc, String text) {
        doc.createParagraph().createRun().setText(text);
    }

    private static void heading(org.apache.poi.xwpf.usermodel.XWPFDocument doc, String text) {
        var p = doc.createParagraph();
        var r = p.createRun();
        r.setText(text);
        r.setBold(true);
        r.setFontSize(13);
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
