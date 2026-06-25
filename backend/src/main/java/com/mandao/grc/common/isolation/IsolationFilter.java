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

    private final VisibleOrgsService visibleOrgsService;
    private final JwtService jwtService;

    public IsolationFilter(VisibleOrgsService visibleOrgsService, JwtService jwtService) {
        this.visibleOrgsService = visibleOrgsService;
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String username = jwtService.verifyUsername(tokenFromCookie(request));
        if (username == null) {
            username = request.getHeader("X-User"); // 开发/测试回退
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
