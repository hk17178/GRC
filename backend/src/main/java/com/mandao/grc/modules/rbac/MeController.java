package com.mandao.grc.modules.rbac;

import com.mandao.grc.common.auth.CurrentUserContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 当前用户权限端点：/api/me。供前端登录后拉取有效权限以做菜单/按钮门控。
 */
@RestController
@RequestMapping("/api/me")
public class MeController {

    private final RbacPermissionService permissionService;

    public MeController(RbacPermissionService permissionService) {
        this.permissionService = permissionService;
    }

    /** 当前用户的有效权限映射 {resourceCode: RW/RO}（不含 HIDDEN；缺省即隐藏）。 */
    @GetMapping("/permissions")
    public Map<String, String> permissions() {
        return permissionService.effectiveFor(CurrentUserContext.get());
    }
}
