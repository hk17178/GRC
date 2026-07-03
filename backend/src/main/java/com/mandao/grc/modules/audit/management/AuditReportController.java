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

    /** 生成报告草稿（自动组稿：计划+发现五要素+整改台账；幂等）。 */
    @PostMapping
    @RequiresPermission("extaudit")
    public AuditReport createDraft(@RequestBody CreateReportRequest req,
                                   @RequestHeader(value = "X-User", required = false) String user) {
        return service.createDraft(req.planId(), actor(user));
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

    private String actor(String user) {
        String current = CurrentUserContext.get();
        if (current != null && !current.isBlank()) {
            return current;
        }
        return (user == null || user.isBlank()) ? "anonymous" : user;
    }

    /** 生成草稿请求体。 */
    public record CreateReportRequest(Long planId) {
    }

    /** 编辑报告请求体。 */
    public record EditReportRequest(String title, AuditOpinion opinion, String summary, String content) {
    }
}
