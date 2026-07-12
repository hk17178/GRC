package com.mandao.grc.common.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录按来源 IP 限速（安全评审 M-16）。
 *
 * 现有失败锁定按【账号】计数，既挡不住"分散账号爆破"，又可被攻击者拿来定向锁死某受害账号(DoS)。
 * 补一层按 IP 的滑窗限速：单 IP 在窗口内登录尝试超上限即拒（429）；登录成功清零该 IP。
 * 内存实现，适合单实例；多实例部署需换共享存储(如 Redis)方能全局生效。
 */
@Component
public class LoginRateLimiter {

    private static final class Bucket {
        long windowStart;
        int count;
    }

    private final int maxAttempts;
    private final long windowMs;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public LoginRateLimiter(@Value("${grc.auth.login-rate.max:20}") int maxAttempts,
                            @Value("${grc.auth.login-rate.window-seconds:300}") long windowSeconds) {
        this.maxAttempts = Math.max(1, maxAttempts);
        this.windowMs = Math.max(1, windowSeconds) * 1000L;
    }

    /** 记一次来自该 IP 的登录尝试；窗口内超过上限返回 false（应拒绝）。 */
    public boolean allow(String ip) {
        if (ip == null || ip.isBlank()) {
            ip = "unknown";
        }
        // 粗略防内存无界增长（攻击者轮换 IP）：桶数过多时清空重来，仅短暂放宽限速
        if (buckets.size() > 50_000) {
            buckets.clear();
        }
        long now = System.currentTimeMillis();
        Bucket b = buckets.computeIfAbsent(ip, k -> new Bucket());
        synchronized (b) {
            if (now - b.windowStart > windowMs) {
                b.windowStart = now;
                b.count = 0;
            }
            b.count++;
            return b.count <= maxAttempts;
        }
    }

    /** 登录成功后清零该 IP 计数（避免正常用户被历史失败拖累）。 */
    public void reset(String ip) {
        if (ip != null) {
            buckets.remove(ip);
        }
    }
}
