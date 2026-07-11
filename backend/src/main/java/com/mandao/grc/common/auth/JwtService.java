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

    /** 曾经的硬编码开发默认值——显式拒绝，防误配为可预测密钥被伪造令牌（安全评审 H-9）。 */
    private static final String KNOWN_INSECURE_DEFAULT = "dev-only-secret-change-me-please-32bytes!!";

    public JwtService(@Value("${grc.auth.jwt.secret:}") String secret,
                      @Value("${grc.auth.jwt.ttl-hours:12}") long ttlHours) {
        // fail-fast：密钥缺失/为已知默认/过短一律拒绝启动，不再补齐弱密钥（HS256 需 ≥256bit）
        if (secret == null || secret.isBlank() || KNOWN_INSECURE_DEFAULT.equals(secret)) {
            throw new IllegalStateException(
                    "JWT 签名密钥未配置或为已知默认值——请设置强随机 GRC_JWT_SECRET（≥32 字节）后再启动");
        }
        byte[] raw = secret.getBytes(StandardCharsets.UTF_8);
        if (raw.length < 32) {
            throw new IllegalStateException(
                    "JWT 签名密钥过短，须 ≥32 字节（256bit），当前 " + raw.length + " 字节");
        }
        this.key = Keys.hmacShaKeyFor(raw);
        this.ttlMillis = ttlHours * 3600_000L;
    }

    /** 签发令牌。mustChange=true 时带 mc 声明，供网关强制首登改密（安全评审 M-14）。 */
    public String issue(Long userId, String username, boolean mustChange) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(username)
                .claim("uid", userId)
                .claim("mc", mustChange)
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

    /** 令牌是否带"须改密"标记（M-14）；无效/过期/无该声明均返回 false。 */
    public boolean isMustChange(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            Claims c = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            return Boolean.TRUE.equals(c.get("mc", Boolean.class));
        } catch (Exception e) {
            return false;
        }
    }

    /** Cookie 有效期（秒），与令牌 TTL 一致。 */
    public int cookieMaxAgeSeconds() {
        return (int) (ttlMillis / 1000);
    }
}
