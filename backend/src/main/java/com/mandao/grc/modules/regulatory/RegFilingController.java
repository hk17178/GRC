package com.mandao.grc.modules.regulatory;

import com.mandao.grc.modules.rbac.RequiresPermission;
import com.mandao.grc.modules.workflow.ApprovalDecision;
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
 * 报送日历 REST 端点：/api/reg-filings（参照 AuditPlanController 风格）。
 *
 * 隔离/actor：可见范围由 X-User 头决定（切面注入 visible_orgs），actor 取 X-User。
 * 报送生命周期：TO_DRAFT → DRAFTING → SUBMITTED → CLOSED。
 */
@RestController
@RequestMapping("/api/reg-filings")
public class RegFilingController {

    private final RegFilingService service;

    public RegFilingController(RegFilingService service) {
        this.service = service;
    }

    @GetMapping
    public List<RegFiling> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public RegFiling get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @RequiresPermission("regaffairs")
    public RegFiling create(@RequestBody CreateFilingRequest req,
                            @RequestHeader(value = "X-User", required = false) String user) {
        return service.create(req.orgId(), req.title(), req.regulator(), req.statutoryDeadline(), actor(user));
    }

    @PostMapping("/{id}/prepare")
    @RequiresPermission("regaffairs")
    public RegFiling prepare(@PathVariable Long id,
                             @RequestHeader(value = "X-User", required = false) String user) {
        return service.prepare(id, actor(user));
    }

    /** 提交内部复核（DRAFTING → PENDING_REVIEW，启动审批）。 */
    @PostMapping("/{id}/submit-for-review")
    @RequiresPermission("regaffairs")
    public RegFiling submitForReview(@PathVariable Long id,
                                     @RequestHeader(value = "X-User", required = false) String user) {
        return service.submitForReview(id, actor(user));
    }

    /** 复核通过 → 正式报送（PENDING_REVIEW → SUBMITTED）。 */
    @PostMapping("/{id}/approve-submit")
    @RequiresPermission("regaffairs")
    public RegFiling approveSubmit(@PathVariable Long id,
                                   @RequestBody(required = false) DecideRequest req,
                                   @RequestHeader(value = "X-User", required = false) String user) {
        return service.decideSubmit(id, ApprovalDecision.APPROVED, actor(user), req == null ? null : req.comment());
    }

    /** 复核驳回 → 退回起草（PENDING_REVIEW → DRAFTING）。 */
    @PostMapping("/{id}/reject-submit")
    @RequiresPermission("regaffairs")
    public RegFiling rejectSubmit(@PathVariable Long id,
                                  @RequestBody(required = false) DecideRequest req,
                                  @RequestHeader(value = "X-User", required = false) String user) {
        return service.decideSubmit(id, ApprovalDecision.REJECTED, actor(user), req == null ? null : req.comment());
    }

    @PostMapping("/{id}/close")
    @RequiresPermission("regaffairs")
    public RegFiling close(@PathVariable Long id,
                           @RequestHeader(value = "X-User", required = false) String user) {
        return service.close(id, actor(user));
    }

    private String actor(String user) {
        return (user == null || user.isBlank()) ? "anonymous" : user;
    }

    /** 新建报送事项请求体。 */
    public record CreateFilingRequest(Long orgId, String title, String regulator, LocalDate statutoryDeadline) {
    }

    /** 复核处置请求体（意见可选）。 */
    public record DecideRequest(String comment) {
    }
}
