package com.mandao.grc.modules.permission;

import org.springframework.web.bind.annotation.GetMapping;
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

    /** 授予角色（SoD 互斥且无豁免则抛 SodViolationException）。 */
    @PostMapping("/grant")
    public UserRoleOrg grant(@RequestBody GrantRequest req,
                             @RequestHeader(value = "X-User", required = false) String user) {
        return service.grantRole(req.orgId(), req.userId(), req.roleId(), actor(user));
    }

    /** 回收角色（置 active=false）。 */
    @PostMapping("/revoke")
    public UserRoleOrg revoke(@RequestBody GrantRequest req,
                              @RequestHeader(value = "X-User", required = false) String user) {
        return service.revokeRole(req.orgId(), req.userId(), req.roleId(), actor(user));
    }

    /** 登记 SoD 豁免（经审批），放行原本被互斥拦截的授权。 */
    @PostMapping("/sod-exception")
    public SodException grantSodException(@RequestBody SodExceptionRequest req,
                                          @RequestHeader(value = "X-User", required = false) String user) {
        return service.grantSodException(req.orgId(), req.userId(), req.sodRuleId(), actor(user), req.reason());
    }

    private String actor(String user) {
        return (user == null || user.isBlank()) ? "anonymous" : user;
    }

    /** 授予/回收请求体（权限四元组 org × user × role）。 */
    public record GrantRequest(Long orgId, Long userId, Long roleId) {
    }

    /** SoD 豁免登记请求体。 */
    public record SodExceptionRequest(Long orgId, Long userId, Long sodRuleId, String reason) {
    }
}
