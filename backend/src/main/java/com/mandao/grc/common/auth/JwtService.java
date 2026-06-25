package com.mandao.grc.common.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 签发与校验（HS256）。
 *
 * 密钥由配置/环境注入（grc.auth.jwt.secret ← ${GRC_JWT_SECRET}），<b>代码不含生产密钥</b>；
 * 令牌承载 sub=username、uid=用户 id，含签发/过期时间。校验失败返回 null（调用方按未认证处理）。
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final long ttlMillis;

    public JwtService(@Value("${grc.auth.jwt.secret:dev-only-secret-change-me-please-32bytes!!}") String secret,
                      @Value("${grc.auth.jwt.ttl-hours:12}") long ttlHours) {
        // HS256 需 ≥256bit 密钥；不足则补齐避免启动失败（生产务必配足够长的密钥）
        byte[] raw = secret.getBytes(StandardCharsets.UTF_8);
        if (raw.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(raw, 0, padded, 0, raw.length);
            for (int i = raw.length; i < 32; i++) {
                padded[i] = (byte) ('0' + (i % 9));
            }
            raw = padded;
        }
        this.key = Keys.hmacShaKeyFor(raw);
        this.ttlMillis = ttlHours * 3600_000L;
    }

    /** 签发令牌。注意：时间戳由调用上下文驱动（new Date() 在生产容器内可用）。 */
    public String issue(Long userId, String username) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(username)
                .claim("uid", userId)
                .issuedAt(new Date(now))
                .expiration(new Date(now + ttlMillis))
                .signWith(key)
                .compact();
    }

    /** 校验并取用户名（sub）；无效/过期返回 null。 */
    public String verifyUsername(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            Claims c = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            return c.getSubject();
        } catch (Exception e) {
            return null;
        }
    }

    /** Cookie 有效期（秒），与令牌 TTL 一致。 */
    public int cookieMaxAgeSeconds() {
        return (int) (ttlMillis / 1000);
    }
}
