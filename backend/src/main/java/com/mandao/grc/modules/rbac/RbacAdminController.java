package com.mandao.grc.modules.rbac;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * RBAC 配置 REST 端点：/api/rbac（角色 + 资源目录 + 权限矩阵）。
 *
 * 写操作受 @RequiresPermission("rbacconfig") 限制——仅对"权限配置"菜单有 RW 的角色(默认超管)可改。
 */
@RestController
@RequestMapping("/api/rbac")
public class RbacAdminController {

    private final RbacAdminService service;
    private final ResourceRepository resourceRepo;

    public RbacAdminController(RbacAdminService service, ResourceRepository resourceRepo) {
        this.service = service;
        this.resourceRepo = resourceRepo;
    }

    /** 角色列表。 */
    @GetMapping("/roles")
    public List<Map<String, Object>> roles() {
        return service.listRoles();
    }

    /** 用户列表（供"用户授权"选人）。 */
    @GetMapping("/users")
    public List<Map<String, Object>> users() {
        return service.listUsers();
    }

    /** 资源目录（菜单+动作，矩阵的行）。 */
    @GetMapping("/resources")
    public List<Resource> resources() {
        return resourceRepo.findAllByOrderBySortAsc();
    }

    /** 某角色的权限矩阵。 */
    @GetMapping("/roles/{id}/permissions")
    public Map<String, String> rolePermissions(@PathVariable Long id) {
        return service.rolePermissions(id);
    }

    /** 保存某角色的权限矩阵。 */
    @PutMapping("/roles/{id}/permissions")
    @RequiresPermission("rbacconfig")
    public Map<String, Object> setRolePermissions(@PathVariable Long id, @RequestBody Map<String, String> levels) {
        service.setRolePermissions(id, levels);
        return Map.of("ok", true);
    }

    /** 新建角色。 */
    @PostMapping("/roles")
    @RequiresPermission("rbacconfig")
    public Map<String, Object> createRole(@RequestBody CreateRoleRequest req) {
        Long id = service.createRole(req.code(), req.name());
        return Map.of("id", id);
    }

    public record CreateRoleRequest(String code, String name) {
    }
}
