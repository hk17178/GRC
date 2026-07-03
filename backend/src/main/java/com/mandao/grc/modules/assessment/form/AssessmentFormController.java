package com.mandao.grc.modules.assessment.form;

import com.mandao.grc.modules.rbac.RequiresPermission;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 风险评估表单引擎 REST 端点（P1）。
 *
 * 模板侧（/api/assessment-templates/...）：上传 .docx、列出版本、启用——均属风险评估配置，写权限门控 "risk"。
 * 评估侧（/api/assessments/...）：取填写界面（读）、保存填写（写，门控 "risk"）。
 */
@RestController
@RequestMapping("/api")
public class AssessmentFormController {

    private final AssessmentFormService service;

    public AssessmentFormController(AssessmentFormService service) {
        this.service = service;
    }

    // ---------- 模板侧 ----------

    /** 上传 .docx 模板 → 解析 → 生成 DRAFT 表单版本。 */
    @PostMapping("/assessment-templates/{id}/form")
    @RequiresPermission("risk")
    public FormVersionView uploadForm(@PathVariable Long id,
                                      @RequestParam("file") MultipartFile file,
                                      @RequestParam(value = "name", required = false) String name) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("未收到 .docx 文件");
        }
        TemplateForm form = service.uploadForm(id, name, file.getBytes());
        return FormVersionView.of(form, service.schemaOf(form));
    }

    /** 列出某模板的全部表单版本。 */
    @GetMapping("/assessment-templates/{id}/forms")
    public List<FormVersionView> listForms(@PathVariable Long id) {
        return service.listForms(id).stream()
                .map(f -> FormVersionView.meta(f))
                .toList();
    }

    /** 下载某表单版本的 .docx 原件（R4 模板中心：模板预览/取回官方模板）。 */
    @GetMapping("/assessment-templates/forms/{formId}/docx")
    public ResponseEntity<byte[]> downloadFormDocx(@PathVariable Long formId) {
        TemplateForm form = service.getForm(formId);
        if (form.getDocx() == null) {
            throw new IllegalStateException("该表单版本无 .docx 原件");
        }
        return download(form.getDocx(), "template-form-" + formId + ".docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    }

    /** 启用某表单版本（同模板仅一条 ACTIVE）。 */
    @PostMapping("/assessment-templates/forms/{formId}/activate")
    @RequiresPermission("risk")
    public FormVersionView activate(@PathVariable Long formId) {
        TemplateForm form = service.activate(formId);
        return FormVersionView.of(form, service.schemaOf(form));
    }

    // ---------- 评估侧 ----------

    /** 取某评估的填写界面（schema + answers）。 */
    @GetMapping("/assessments/{id}/form")
    public AssessmentFormService.AssessmentFormView assessmentForm(@PathVariable Long id) {
        return service.getAssessmentForm(id);
    }

    /** 保存某评估的填写值，返回聚合出的整体残余等级（驱动看板/任务列表 + 完成门控）。 */
    @PutMapping("/assessments/{id}/answers")
    @RequiresPermission("risk")
    public Map<String, Object> saveAnswers(@PathVariable Long id, @RequestBody Object answers) {
        var level = service.saveAnswers(id, answers);
        return java.util.Collections.singletonMap("riskLevel", level == null ? null : level.name());
    }

    /** 导出回填后的报告 Word（.docx，格式同上传的官方模板）。 */
    @GetMapping("/assessments/{id}/report.docx")
    public ResponseEntity<byte[]> reportDocx(@PathVariable Long id) {
        byte[] body = service.buildReportDocx(id);
        return download(body, "risk-assessment-" + id + ".docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    }

    /** 导出回填后的报告 PDF（LibreOffice 转，可直接交审计）。 */
    @GetMapping("/assessments/{id}/report.pdf")
    public ResponseEntity<byte[]> reportPdf(@PathVariable Long id) {
        byte[] body = service.buildReportPdf(id);
        return download(body, "risk-assessment-" + id + ".pdf", "application/pdf");
    }

    /** 组装下载响应（附件 + 内容类型）。 */
    private ResponseEntity<byte[]> download(byte[] body, String filename, String contentType) {
        return ResponseEntity.ok()
                .header("Content-Type", contentType)
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .body(body);
    }

    /**
     * 表单版本视图。schema 仅在需要时附带（列表接口不带 docx/schema 以省流量）。
     */
    public record FormVersionView(Long id, Long templateId, Integer versionNo, String name,
                                  String status, FormSchema schema) {
        static FormVersionView meta(TemplateForm f) {
            return new FormVersionView(f.getId(), f.getTemplateId(), f.getVersionNo(), f.getName(), f.getStatus(), null);
        }
        static FormVersionView of(TemplateForm f, FormSchema schema) {
            return new FormVersionView(f.getId(), f.getTemplateId(), f.getVersionNo(), f.getName(), f.getStatus(), schema);
        }
    }
}
