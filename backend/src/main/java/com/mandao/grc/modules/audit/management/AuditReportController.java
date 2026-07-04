package com.mandao.grc.modules.audit.management;

import com.mandao.grc.common.auth.CurrentUserContext;
import com.mandao.grc.modules.rbac.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 审计报告 REST 端点（V47 · A1）：/api/audit-reports。
 *
 * 生命周期：草稿(自动组稿) → 征求意见 → 定稿(须选意见) → 签发(终态)。写门控 "extaudit"。
 */
@RestController
@RequestMapping("/api/audit-reports")
public class AuditReportController {

    private final AuditReportService service;

    public AuditReportController(AuditReportService service) {
        this.service = service;
    }

    /** 按计划取报告（无则 null——前端据此显示「生成报告草稿」）。 */
    @GetMapping
    public AuditReport byPlan(@RequestParam Long planId) {
        return service.byPlan(planId);
    }

    /** 生成报告草稿（自动组稿：计划+发现五要素+整改台账；可选模板作骨架；幂等）。 */
    @PostMapping
    @RequiresPermission("extaudit")
    public AuditReport createDraft(@RequestBody CreateReportRequest req,
                                   @RequestHeader(value = "X-User", required = false) String user) {
        return service.createDraft(req.planId(), req.templateId(), actor(user));
    }

    // ---------- 报告模板管理（V54） ----------

    /** 报告模板清单。 */
    @GetMapping("/templates")
    public java.util.List<AuditReportTemplate> listTemplates() {
        return service.listTemplates();
    }

    /** 新建报告模板。 */
    @PostMapping("/templates")
    @RequiresPermission("extaudit")
    public AuditReportTemplate createTemplate(@RequestBody TemplateRequest req,
                                              @RequestHeader(value = "X-User", required = false) String user) {
        return service.createTemplate(req.orgId(), req.name(), req.category(), req.content(), actor(user));
    }

    /** 编辑报告模板。 */
    @PutMapping("/templates/{id}")
    @RequiresPermission("extaudit")
    public AuditReportTemplate updateTemplate(@PathVariable Long id, @RequestBody TemplateRequest req,
                                              @RequestHeader(value = "X-User", required = false) String user) {
        return service.updateTemplate(id, req.name(), req.category(), req.content(), actor(user));
    }

    /** 启停报告模板。 */
    @PutMapping("/templates/{id}/enabled")
    @RequiresPermission("extaudit")
    public AuditReportTemplate setTemplateEnabled(@PathVariable Long id, @RequestParam boolean enabled) {
        return service.setTemplateEnabled(id, enabled);
    }

    /** 删除报告模板。 */
    @org.springframework.web.bind.annotation.DeleteMapping("/templates/{id}")
    @RequiresPermission("extaudit")
    public void deleteTemplate(@PathVariable Long id,
                               @RequestHeader(value = "X-User", required = false) String user) {
        service.deleteTemplate(id, actor(user));
    }

    /** 报告模板请求体（V54）。 */
    public record TemplateRequest(Long orgId, String name, String category, String content) {
    }

    /** 编辑报告（标题/意见/总体评价/正文；定稿后冻结）。 */
    @PutMapping("/{id}")
    @RequiresPermission("extaudit")
    public AuditReport update(@PathVariable Long id,
                              @RequestBody EditReportRequest req,
                              @RequestHeader(value = "X-User", required = false) String user) {
        return service.update(id, req.title(), req.opinion(), req.summary(), req.content(), actor(user));
    }

    /** 征求意见：DRAFT → COMMENTING。 */
    @PostMapping("/{id}/comment")
    @RequiresPermission("extaudit")
    public AuditReport submitComment(@PathVariable Long id,
                                     @RequestHeader(value = "X-User", required = false) String user) {
        return service.submitComment(id, actor(user));
    }

    /** 定稿：COMMENTING → FINAL（须已选审计意见）。 */
    @PostMapping("/{id}/finalize")
    @RequiresPermission("extaudit")
    public AuditReport finalizeReport(@PathVariable Long id,
                                      @RequestHeader(value = "X-User", required = false) String user) {
        return service.finalizeReport(id, actor(user));
    }

    /** 签发：FINAL → ISSUED（终态）。 */
    @PostMapping("/{id}/issue")
    @RequiresPermission("extaudit")
    public AuditReport issue(@PathVariable Long id,
                             @RequestHeader(value = "X-User", required = false) String user) {
        return service.issue(id, actor(user));
    }

    /** 报告导出 .docx（V52 文书套打）。 */
    @GetMapping("/{id}/docx")
    public org.springframework.http.ResponseEntity<byte[]> reportDocx(@PathVariable Long id) {
        byte[] body = service.buildReportDocx(id);
        return org.springframework.http.ResponseEntity.ok()
                .header("Content-Type", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                .header("Content-Disposition", "attachment; filename=\"audit-report-" + id + ".docx\"")
                .body(body);
    }

    private String actor(String user) {
        String current = CurrentUserContext.get();
        if (current != null && !current.isBlank()) {
            return current;
        }
        return com.mandao.grc.common.auth.ActorResolver.resolve(user); // 七轮 7-4：登录态优先，消除 anonymous 归因
    }

    /** 生成草稿请求体（templateId 可空=不使用模板）。 */
    public record CreateReportRequest(Long planId, Long templateId) {
    }

    /** 编辑报告请求体。 */
    public record EditReportRequest(String title, AuditOpinion opinion, String summary, String content) {
    }
}
