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

import java.util.List;

/**
 * 审计发现 REST 端点：/api/audit-findings（参照 RiskFindingController 风格）。
 *
 * 隔离/actor 同 {@link AuditPlanController}：可见范围由 X-User 头决定，actor 取 X-User。
 *
 * 外审对外回函三段漏斗（红线）：submit/accept/confirm-close 三端点单向推进 external_response_status；
 * 跳级/逆向/非外审走漏斗由 Service 抛 {@link AuditFunnelException} 阻断。
 */
@RestController
@RequestMapping("/api/audit-findings")
public class AuditFindingController {

    private final AuditFindingService service;

    public AuditFindingController(AuditFindingService service) {
        this.service = service;
    }

    /** 列出某审计计划下的发现。 */
    @GetMapping
    public List<AuditFinding> listByPlan(@RequestParam(required = false) Long auditPlanId,
                                          @RequestParam(required = false) AuditType type) {
        // 二选一：按计划查（内审页逐计划视图）或按类型跨计划查（外审页汇总视图）
        if (auditPlanId != null) {
            return service.listByPlan(auditPlanId);
        }
        if (type != null) {
            return service.listByType(type);
        }
        throw new IllegalArgumentException("请提供 auditPlanId 或 type 参数");
    }

    /** 取单个审计发现。 */
    @GetMapping("/{id}")
    public AuditFinding get(@PathVariable Long id) {
        return service.get(id);
    }

    /** 新建审计发现（OPEN 态）。 */
    @PostMapping
    @RequiresPermission("extaudit")
    public AuditFinding create(@RequestBody CreateFindingRequest req,
                               @RequestHeader(value = "X-User", required = false) String user) {
        return service.createFinding(req.orgId(), req.auditPlanId(), req.title(), req.severity(), actor(user));
    }

    /** 调整严重度。 */
    @PostMapping("/{id}/severity")
    @RequiresPermission("extaudit")
    public AuditFinding setSeverity(@PathVariable Long id,
                                    @RequestBody SeverityRequest req,
                                    @RequestHeader(value = "X-User", required = false) String user) {
        return service.setSeverity(id, req.severity(), actor(user));
    }

    // ---------- 内部处置状态机 ----------

    /** 开始分析：OPEN → ANALYZING。 */
    @PostMapping("/{id}/analyze")
    @RequiresPermission("extaudit")
    public AuditFinding analyze(@PathVariable Long id,
                                @RequestHeader(value = "X-User", required = false) String user) {
        return service.analyze(id, actor(user));
    }

    /** 完成整改：ANALYZING → REMEDIATED。 */
    @PostMapping("/{id}/remediate")
    @RequiresPermission("extaudit")
    public AuditFinding remediate(@PathVariable Long id,
                                  @RequestHeader(value = "X-User", required = false) String user) {
        return service.remediate(id, actor(user));
    }

    /** 关闭发现：REMEDIATED → CLOSED（内部处置终态）。 */
    @PostMapping("/{id}/close")
    @RequiresPermission("extaudit")
    public AuditFinding closeFinding(@PathVariable Long id,
                                     @RequestHeader(value = "X-User", required = false) String user) {
        return service.closeFinding(id, actor(user));
    }

    // ---------- 外审对外回函三段漏斗（红线） ----------

    /** 漏斗第一段：提交外部机构（→ SUBMITTED）。仅外审。 */
    @PostMapping("/{id}/external-response/submit")
    @RequiresPermission("extaudit")
    public AuditFinding submitResponse(@PathVariable Long id,
                                       @RequestHeader(value = "X-User", required = false) String user) {
        return service.submitResponse(id, actor(user));
    }

    /** 漏斗第二段：外方受理（→ ACCEPTED）。仅外审。 */
    @PostMapping("/{id}/external-response/accept")
    @RequiresPermission("extaudit")
    public AuditFinding acceptResponse(@PathVariable Long id,
                                       @RequestHeader(value = "X-User", required = false) String user) {
        return service.acceptResponse(id, actor(user));
    }

    /** 漏斗第三段（闭环）：外方确认关闭（→ CONFIRMED_CLOSED）。仅外审。 */
    @PostMapping("/{id}/external-response/confirm-close")
    @RequiresPermission("extaudit")
    public AuditFinding confirmClose(@PathVariable Long id,
                                     @RequestHeader(value = "X-User", required = false) String user) {
        return service.confirmClose(id, actor(user));
    }

    // ---------- 五要素 + 管理层回应（V47 · IIA 4C+R） ----------

    /** 五要素补全：现状/标准/原因/影响/建议（闭环后冻结）。 */
    @org.springframework.web.bind.annotation.PutMapping("/{id}/detail")
    @RequiresPermission("extaudit")
    public AuditFinding setDetail(@PathVariable Long id,
                                  @RequestBody DetailRequest req,
                                  @RequestHeader(value = "X-User", required = false) String user) {
        return service.setDetail(id, req.conditionDesc(), req.criteriaDesc(),
                req.cause(), req.effect(), req.recommendation(), actor(user));
    }

    /** 管理层回应：被审计单位意见/整改承诺（回应人=登录人）。 */
    @PostMapping("/{id}/response")
    @RequiresPermission("extaudit")
    public AuditFinding respond(@PathVariable Long id,
                                @RequestBody ResponseRequest req,
                                @RequestHeader(value = "X-User", required = false) String user) {
        return service.respond(id, req.response(), actor(user));
    }

    private String actor(String user) {
        String current = com.mandao.grc.common.auth.CurrentUserContext.get();
        if (current != null && !current.isBlank()) {
            return current;
        }
        return com.mandao.grc.common.auth.ActorResolver.resolve(user); // 七轮 7-4：登录态优先，消除 anonymous 归因
    }

    /** 新建审计发现请求体。 */
    public record CreateFindingRequest(Long orgId, Long auditPlanId, String title, AuditSeverity severity) {
    }

    /** 严重度请求体。 */
    public record SeverityRequest(AuditSeverity severity) {
    }

    /** 五要素请求体（V47）。 */
    public record DetailRequest(String conditionDesc, String criteriaDesc,
                                String cause, String effect, String recommendation) {
    }

    /** 管理层回应请求体（V47）。 */
    public record ResponseRequest(String response) {
    }
}
