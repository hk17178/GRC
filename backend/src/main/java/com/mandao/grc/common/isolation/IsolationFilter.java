package com.mandao.grc.common.isolation;

import com.mandao.grc.common.auth.AuthController;
import com.mandao.grc.common.auth.CurrentUserContext;
import com.mandao.grc.common.auth.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 请求入口处建立认证主体 + 隔离上下文。
 *
 * 认证主体解析优先级：
 *   1) httpOnly Cookie grc_token 的 JWT（生产正途，增强③ R1 接入）；
 *   2) 回退到请求头 X-User（开发/测试占位——保持既有 Testcontainers/HTTP 冒烟零改动）。
 * 解析出 username 后：置 {@link CurrentUserContext}，并由 {@link VisibleOrgsService} 计算 visibleOrgs 置 {@link IsolationContext}。
 *
 * 始终在 finally 清理 ThreadLocal，避免线程池复用泄漏。
 */
@Component
public class IsolationFilter extends OncePerRequestFilter {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(IsolationFilter.class);

    private final VisibleOrgsService visibleOrgsService;
    private final JwtService jwtService;

    /**
     * X-User 头回退开关（安全评审 C-1）：<b>默认关闭（secure-by-default）</b>。
     * 开启后未认证请求可用 X-User 头冒充任意用户——仅供开发/联调/测试免登录冒烟，生产切勿开启。
     * 开发/测试如需：显式 grc.auth.header-fallback.enabled=true（测试在 src/test/resources/application.properties 已开）。
     */
    private final boolean headerFallbackEnabled;

    public IsolationFilter(VisibleOrgsService visibleOrgsService, JwtService jwtService,
                           @org.springframework.beans.factory.annotation.Value(
                                   "${grc.auth.header-fallback.enabled:false}") boolean headerFallbackEnabled) {
        this.visibleOrgsService = visibleOrgsService;
        this.jwtService = jwtService;
        this.headerFallbackEnabled = headerFallbackEnabled;
        // 启动自检：回退开着必须刺眼地喊出来，避免带病上线
        if (headerFallbackEnabled) {
            log.warn("[安全自检] X-User 头认证回退已启用（开发/联调模式）——生产环境必须设置 "
                    + "grc.auth.header-fallback.enabled=false，否则未认证请求可冒充任意用户");
        } else {
            log.info("[安全自检] X-User 头认证回退已关闭，仅接受 JWT Cookie 认证");
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String username = jwtService.verifyUsername(tokenFromCookie(request));
        if (username == null && headerFallbackEnabled) {
            username = request.getHeader("X-User"); // 开发/测试回退（生产关闭）
        }
        // 全局认证前置（安全评审 P0-2）：未认证的 /api 业务请求（登录/登出/品牌读取等公开端点除外）
        // 一律 401，不再进入业务——堵住「读端点漏挂注解 + 底表无 RLS」被匿名越权/跨租户读取的整片攻击面。
        if ((username == null || username.isBlank()) && requiresAuth(request)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":\"UNAUTHENTICATED\",\"message\":\"未登录或会话已过期\"}");
            return;
        }
        try {
            if (username != null && !username.isBlank()) {
                CurrentUserContext.set(username);
                IsolationContext.set(visibleOrgsService.computeFor(username));
            }
            chain.doFilter(request, response);
        } finally {
            IsolationContext.clear();
            CurrentUserContext.clear();
        }
    }

    /**
     * 该请求是否要求已认证。仅拦截 /api 业务端点；放行认证前必达的公开端点：
     * 登录、登出、以及登录页读取的品牌信息(GET /api/branding)。其余 /api 一律需认证。
     * 非 /api（actuator 健康探针、静态资源等）不在此约束。
     */
    private boolean requiresAuth(HttpServletRequest request) {
        String p = request.getRequestURI();
        if (p == null || !p.startsWith("/api/")) {
            return false;
        }
        String method = request.getMethod();
        if (("/api/auth/login".equals(p) || "/api/auth/logout".equals(p)) && "POST".equalsIgnoreCase(method)) {
            return false;   // 登录/登出公开
        }
        if ("/api/branding".equals(p) && "GET".equalsIgnoreCase(method)) {
            return false;   // 登录页品牌读取公开（PUT 写入仍需认证）
        }
        return true;
    }

    /** 从 Cookie 取 JWT 令牌。 */
    private String tokenFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie c : cookies) {
                if (AuthController.COOKIE.equals(c.getName())) {
                    return c.getValue();
                }
            }
        }
        return null;
    }
}
