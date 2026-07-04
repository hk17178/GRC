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

    /** 绑定检查表模板（复用 M2 表单引擎的评估模板；已执行后不可换绑）。 */
    @PostMapping("/{id}/checklist/bind")
    @RequiresPermission("extaudit")
    public AuditPlan bindChecklist(@PathVariable Long id,
                                   @RequestBody BindChecklistRequest req,
                                   @RequestHeader(value = "X-User", required = false) String user) {
        return service.bindChecklist(id, req.templateId(), actor(user));
    }

    /** 执行检查表：按绑定模板生成评估（幂等，已执行返回既有）。 */
    @PostMapping("/{id}/checklist/start")
    @RequiresPermission("extaudit")
    public AuditPlan startChecklist(@PathVariable Long id,
                                    @RequestHeader(value = "X-User", required = false) String user) {
        return service.startChecklist(id, actor(user));
    }

    /** actor 归属：优先 JWT 登录人（底稿执行/复核人须为真实登录人），X-User 头兜底。 */
    private String actor(String user) {
        String current = com.mandao.grc.common.auth.CurrentUserContext.get();
        if (current != null && !current.isBlank()) {
            return current;
        }
        return com.mandao.grc.common.auth.ActorResolver.resolve(user); // 七轮 7-4：登录态优先，消除 anonymous 归因
    }

    /** 新建审计计划请求体。 */
    public record CreatePlanRequest(Long orgId, String title, AuditType auditType, LocalDate planStartDate) {
    }

    /** 绑定检查表模板请求体。 */
    public record BindChecklistRequest(Long templateId) {
    }

    // ---------- 审计通知书 + 程序/底稿（V50 · A2） ----------

    /** 保存/签发审计通知书（issue=true 签发，签发后冻结）。 */
    @PostMapping("/{id}/notice")
    @RequiresPermission("extaudit")
    public AuditPlan saveNotice(@PathVariable Long id,
                                @RequestBody NoticeRequest req,
                                @RequestHeader(value = "X-User", required = false) String user) {
        return service.saveNotice(id, req.auditee(), req.noticeScope(), req.noticeBasis(),
                req.auditTeam(), req.issue() != null && req.issue(), actor(user));
    }

    /** 计划的审计程序/底稿清单。 */
    @GetMapping("/{id}/procedures")
    public List<AuditProcedure> listProcedures(@PathVariable Long id) {
        return service.listProcedures(id);
    }

    /** 新增审计程序（底稿编号自动 WP-{plan}-{seq}）。 */
    @PostMapping("/{id}/procedures")
    @RequiresPermission("extaudit")
    public AuditProcedure addProcedure(@PathVariable Long id,
                                       @RequestBody ProcedureRequest req,
                                       @RequestHeader(value = "X-User", required = false) String user) {
        return service.addProcedure(id, req.name(), req.objective(), actor(user));
    }

    /** 执行程序（落工作底稿，执行记录必填）。 */
    @PostMapping("/procedures/{procedureId}/execute")
    @RequiresPermission("extaudit")
    public AuditProcedure executeProcedure(@PathVariable Long procedureId,
                                           @RequestBody ExecuteRequest req,
                                           @RequestHeader(value = "X-User", required = false) String user) {
        return service.executeProcedure(procedureId, req.result(), actor(user));
    }

    /** 复核底稿（复核人≠执行人）。 */
    @PostMapping("/procedures/{procedureId}/review")
    @RequiresPermission("extaudit")
    public AuditProcedure reviewProcedure(@PathVariable Long procedureId,
                                          @RequestHeader(value = "X-User", required = false) String user) {
        return service.reviewProcedure(procedureId, actor(user));
    }

    /** 通知书请求体（V50）。 */
    public record NoticeRequest(String auditee, String noticeScope, String noticeBasis,
                                String auditTeam, Boolean issue) {
    }

    /** 新增程序请求体（V50）。 */
    public record ProcedureRequest(String name, String objective) {
    }

    /** 执行程序请求体（V50）。 */
    public record ExecuteRequest(String result) {
    }
}
