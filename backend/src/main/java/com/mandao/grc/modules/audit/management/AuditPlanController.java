package com.mandao.grc.modules.audit.management;

import com.mandao.grc.modules.rbac.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 审计计划 REST 端点：/api/audit-plans（参照 PolicyController/AssessmentController 风格）。
 *
 * 隔离/actor：可见范围由 X-User 头决定（切面注入 visible_orgs），actor 取 X-User。
 *
 * 计划生命周期：PLANNED → IN_PROGRESS → REPORTING → CLOSED；另 PLANNED/IN_PROGRESS → CANCELLED；
 * 非法流转由 Service 抛 IllegalStateException。
 */
@RestController
@RequestMapping("/api/audit-plans")
public class AuditPlanController {

    private final AuditPlanService service;

    public AuditPlanController(AuditPlanService service) {
        this.service = service;
    }

    /** 列出可见范围内的审计计划；可按 type 过滤（INTERNAL/EXTERNAL/REGULATORY）分内外审视图。 */
    @GetMapping
    public List<AuditPlan> list(@RequestParam(required = false) AuditType type) {
        return service.listByType(type);
    }

    /** 取单个审计计划。 */
    @GetMapping("/{id}")
    public AuditPlan get(@PathVariable Long id) {
        return service.get(id);
    }

    /** 新建审计计划（PLANNED 态）。 */
    @PostMapping
    @RequiresPermission("extaudit")
    public AuditPlan create(@RequestBody CreatePlanRequest req,
                            @RequestHeader(value = "X-User", required = false) String user) {
        return service.create(req.orgId(), req.title(), req.auditType(), req.planStartDate(), actor(user));
    }

    /** 开始审计：PLANNED → IN_PROGRESS。 */
    @PostMapping("/{id}/start")
    @RequiresPermission("extaudit")
    public AuditPlan start(@PathVariable Long id,
                           @RequestHeader(value = "X-User", required = false) String user) {
        return service.start(id, actor(user));
    }

    /** 出具报告：IN_PROGRESS → REPORTING。 */
    @PostMapping("/{id}/report")
    @RequiresPermission("extaudit")
    public AuditPlan report(@PathVariable Long id,
                            @RequestHeader(value = "X-User", required = false) String user) {
        return service.report(id, actor(user));
    }

    /** 关闭审计：REPORTING → CLOSED。 */
    @PostMapping("/{id}/close")
    @RequiresPermission("extaudit")
    public AuditPlan close(@PathVariable Long id,
                           @RequestHeader(value = "X-User", required = false) String user) {
        return service.close(id, actor(user));
    }

    /** 取消审计：PLANNED / IN_PROGRESS → CANCELLED。 */
    @PostMapping("/{id}/cancel")
    @RequiresPermission("extaudit")
    public AuditPlan cancel(@PathVariable Long id,
                            @RequestHeader(value = "X-User", required = false) String user) {
        return service.cancel(id, actor(user));
    }

    private String actor(String user) {
        return (user == null || user.isBlank()) ? "anonymous" : user;
    }

    /** 新建审计计划请求体。 */
    public record CreatePlanRequest(Long orgId, String title, AuditType auditType, LocalDate planStartDate) {
    }
}
