package com.mandao.grc.common.auth;

/**
 * 当前请求的认证用户名（ThreadLocal）。由认证过滤器在请求入口设置、finally 清理。
 * 供 /auth/me 与后续权限解析读取当前主体。
 */
public final class CurrentUserContext {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private CurrentUserContext() {
    }

    public static void set(String username) {
        CURRENT.set(username);
    }

    public static String get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
