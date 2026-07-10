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
