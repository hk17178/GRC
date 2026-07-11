package com.mandao.grc.modules.audit.management;

import com.mandao.grc.common.auth.CurrentUserContext;
import com.mandao.grc.modules.rbac.RequiresPermission;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 年度审计计划 + follow-up + 通知书套打 REST 端点（V52 · A3）。写门控 "extaudit"。
 */
@RestController
@RequestMapping("/api")
public class AuditAnnualController {

    private final AuditAnnualService service;
    private final AuditReportService reportService;

    public AuditAnnualController(AuditAnnualService service, AuditReportService reportService) {
        this.service = service;
        this.reportService = reportService;
    }

    /** 年度计划清单（新年度在前）。 */
    @GetMapping("/audit-annual")
    @RequiresPermission("extaudit")
    public List<AuditAnnualPlan> list() {
        return service.list();
    }

    /** 新建年度计划（DRAFT）。 */
    @PostMapping("/audit-annual")
    @RequiresPermission("extaudit")
    public AuditAnnualPlan create(@RequestBody CreateAnnualRequest req,
                                  @RequestHeader(value = "X-User", required = false) String user) {
        return service.create(req.orgId(), req.year(), req.title(), actor(user));
    }

    /** 年度计划的对象清单（风险排序在前）。 */
    @GetMapping("/audit-annual/{id}/items")
    @RequiresPermission("extaudit")
    public List<AuditAnnualItem> items(@PathVariable Long id) {
        return service.listItems(id);
    }

    /** 追加审计对象（仅 DRAFT）。 */
    @PostMapping("/audit-annual/{id}/items")
    @RequiresPermission("extaudit")
    public AuditAnnualItem addItem(@PathVariable Long id,
                                   @RequestBody AddAnnualItemRequest req,
                                   @RequestHeader(value = "X-User", required = false) String user) {
        return service.addItem(id, req.target(), req.riskRank(), req.quarter(), req.note(), actor(user));
    }

    /** 批准年度计划（对象清单冻结）。 */
    @PostMapping("/audit-annual/{id}/approve")
    @RequiresPermission("extaudit")
    public AuditAnnualPlan approve(@PathVariable Long id,
                                   @RequestHeader(value = "X-User", required = false) String user) {
        return service.approve(id, actor(user));
    }

    /** 对象转单项审计计划（防重复立项）。 */
    @PostMapping("/audit-annual/items/{itemId}/to-plan")
    @RequiresPermission("extaudit")
    public AuditAnnualItem toPlan(@PathVariable Long itemId,
                                  @RequestBody(required = false) ToPlanRequest req,
                                  @RequestHeader(value = "X-User", required = false) String user) {
        return service.toPlan(itemId, req == null ? null : req.planStartDate(), actor(user));
    }

    /** 发起后续审计（follow-up，原计划须 CLOSED）。 */
    @PostMapping("/audit-plans/{planId}/follow-up")
    @RequiresPermission("extaudit")
    public AuditPlan followUp(@PathVariable Long planId,
                              @RequestBody(required = false) ToPlanRequest req,
                              @RequestHeader(value = "X-User", required = false) String user) {
        return service.followUp(planId, req == null ? null : req.planStartDate(), actor(user));
    }

    /** 审计通知书导出 .docx（V52 文书套打）。 */
    @GetMapping("/audit-plans/{planId}/notice.docx")
    @RequiresPermission("extaudit")
    public ResponseEntity<byte[]> noticeDocx(@PathVariable Long planId) {
        byte[] body = reportService.buildNoticeDocx(planId);
        return ResponseEntity.ok()
                .header("Content-Type", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                .header("Content-Disposition", "attachment; filename=\"audit-notice-" + planId + ".docx\"")
                .body(body);
    }

    private String actor(String user) {
        String current = CurrentUserContext.get();
        if (current != null && !current.isBlank()) {
            return current;
        }
        return com.mandao.grc.common.auth.ActorResolver.resolve(user); // 七轮 7-4：登录态优先，消除 anonymous 归因
    }

    /** 新建年度计划请求体。 */
    public record CreateAnnualRequest(Long orgId, Integer year, String title) {
    }

    /** 追加对象请求体。 */
    public record AddAnnualItemRequest(String target, Integer riskRank, String quarter, String note) {
    }

    /** 立项/后续审计请求体。 */
    public record ToPlanRequest(LocalDate planStartDate) {
    }
}
