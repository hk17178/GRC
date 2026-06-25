package com.mandao.grc.modules.rbac;

/**
 * 无权限异常（功能级 RBAC 强制拦截）→ 403。与 401(未登录) 区分：已登录但无该操作权限。
 */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
