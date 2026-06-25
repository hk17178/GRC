package com.mandao.grc.modules.rbac;

import com.mandao.grc.common.auth.CurrentUserContext;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

/**
 * 功能级 RBAC 后端强制切面（增强③ R3 · 安全关键）。
 *
 * 在标注 {@link RequiresPermission} 的写接口执行前，校验当前用户(CurrentUserContext)对目标资源有 RW；
 * 未登录或无 RW 则抛 {@link ForbiddenException}（→403）。这是真正的安全边界——前端隐藏只是体验，
 * 后端强制才防绕过。canWrite 自身 @Transactional，经隔离切面读 user_role_org(RLS)，与请求隔离一致。
 */
@Aspect
@Component
public class PermissionEnforcementAspect {

    private final RbacPermissionService permissionService;

    public PermissionEnforcementAspect(RbacPermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @Before("@annotation(req)")
    public void enforce(RequiresPermission req) {
        String username = CurrentUserContext.get();
        if (username == null || username.isBlank()) {
            throw new ForbiddenException("未登录或会话失效");
        }
        if (!permissionService.canWrite(username, req.value())) {
            throw new ForbiddenException("无该操作权限：" + req.value());
        }
    }
}
