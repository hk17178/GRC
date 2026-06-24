package com.mandao.grc.common.isolation;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 请求入口处建立隔离上下文。
 *
 * 本切片（PoC）以请求头 X-User 模拟已认证主体；生产环境改为从安全上下文
 * （AD/本地认证后的 Principal，含 domain_id+username，见 CR-003 多域控）取主体，
 * 再由 {@link VisibleOrgsService} 计算 visibleOrgs。
 *
 * 始终在 finally 清理 ThreadLocal，避免线程池复用泄漏。
 */
@Component
public class IsolationFilter extends OncePerRequestFilter {

    private final VisibleOrgsService visibleOrgsService;

    public IsolationFilter(VisibleOrgsService visibleOrgsService) {
        this.visibleOrgsService = visibleOrgsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String username = request.getHeader("X-User");
        try {
            if (username != null && !username.isBlank()) {
                IsolationContext.set(visibleOrgsService.computeFor(username));
            }
            chain.doFilter(request, response);
        } finally {
            IsolationContext.clear();
        }
    }
}
