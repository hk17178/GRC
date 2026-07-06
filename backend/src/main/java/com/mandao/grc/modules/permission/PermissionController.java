package com.mandao.grc.modules.permission;

import com.mandao.grc.modules.rbac.RequiresPermission;
import com.mandao.grc.modules.workflow.ApprovalDecision;
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
 * 权限授予/回收 REST 端点：/api/permissions（参照 AuditPlanController 风格）。
 *
 * 隔离/actor：可见范围由 X-User 头决定（切面注入 visible_orgs），actor 取 X-User。
 *
 * SoD 红线：grant 触发 {@link SodViolationException} 时由 Service 抛出（互斥且无豁免）；
 * 可先调用 sod-exception 登记豁免后再授权。
 */
@RestController
@RequestMapping("/api/permissions")
public class PermissionController {

    private final PermissionService service;

    public PermissionController(PermissionService service) {
        this.service = service;
    }

    /** 列出某 org 下某 user 的全部授权行（含已回收）。 */
    @GetMapping("/user-roles")
    public List<UserRoleOrg> listUserRoles(@RequestParam Long orgId, @RequestParam Long userId) {
        return service.listUserRoles(orgId, userId);
    }

    /** B18：SoD 存量冲突扫描——盘点现存互斥角色并存（读门控 perm）。 */
    @GetMapping("/sod-conflicts")
    @RequiresPermission("perm")
    public List<PermissionService.SodConflict> scanSodConflicts() {
        return service.scanSodConflicts();
    }

    /** 授予角色（SoD 互斥且无豁免则抛 SodViolationException）。 */
    @PostMapping("/grant")
    @RequiresPermission("perm")
    public UserRoleOrg grant(@RequestBody GrantRequest req,
                             @RequestHeader(value = "X-User", required = false) String user) {
        return service.grantRole(req.orgId(), req.userId(), req.roleId(), actor(user));
    }

    /** 回收角色（置 active=false）。 */
    @PostMapping("/revoke")
    @RequiresPermission("perm")
    public UserRoleOrg revoke(@RequestBody GrantRequest req,
                              @RequestHeader(value = "X-User", required = false) String user) {
        return service.revokeRole(req.orgId(), req.userId(), req.roleId(), actor(user));
    }

    /** 申请 SoD 豁免（A4 审批化）：登记 PENDING 并启动审批，暂不放行（PENDING 不生效）。申请人取 X-User。 */
    @PostMapping("/sod-exceptions")
    @RequiresPermission("perm")
    public SodException requestSodException(@RequestBody SodExceptionRequest req,
                                            @RequestHeader(value = "X-User", required = false) String user) {
        return service.requestSodException(req.orgId(), req.userId(), req.sodRuleId(), actor(user), req.reason(), actor(user));
    }

    /** 审批通过 SoD 豁免：自此放行该规则互斥授权。审批人取 X-User。 */
    @PostMapping("/sod-exceptions/{id}/approve")
    @RequiresPermission("perm")
    public SodException approveSodException(@PathVariable Long id,
                                            @RequestBody(required = false) DecideRequest req,
                                            @RequestHeader(value = "X-User", required = false) String user) {
        return service.decideSodException(id, ApprovalDecision.APPROVED, actor(user), req == null ? null : req.comment());
    }

    /** 审批驳回 SoD 豁免：不放行。审批人取 X-User。 */
    @PostMapping("/sod-exceptions/{id}/reject")
    @RequiresPermission("perm")
    public SodException rejectSodException(@PathVariable Long id,
                                           @RequestBody(required = false) DecideRequest req,
                                           @RequestHeader(value = "X-User", required = false) String user) {
        return service.decideSodException(id, ApprovalDecision.REJECTED, actor(user), req == null ? null : req.comment());
    }

    private String actor(String user) {
        return com.mandao.grc.common.auth.ActorResolver.resolve(user); // 七轮 7-4：登录态优先，消除 anonymous 归因
    }

    /** 授予/回收请求体（权限四元组 org × user × role）。 */
    public record GrantRequest(Long orgId, Long userId, Long roleId) {
    }

    /** SoD 豁免申请请求体。 */
    public record SodExceptionRequest(Long orgId, Long userId, Long sodRuleId, String reason) {
    }

    /** 审批处置请求体（意见可选）。 */
    public record DecideRequest(String comment) {
    }
}
