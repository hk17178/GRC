package com.mandao.grc.common.auth;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 认证端点：/api/auth（登录/登出/当前用户）。
 *
 * 登录成功签发 JWT，置于 <b>httpOnly Cookie</b>（grc_token，抗 XSS——JS 读不到）；浏览器后续请求自动携带，
 * 由认证过滤器校验并建立隔离/权限上下文。Cookie SameSite=Lax 缓解 CSRF。密钥仅环境注入。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /** 令牌 Cookie 名。 */
    public static final String COOKIE = "grc_token";

    private final AppUserRepository userRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthController(AppUserRepository userRepo, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    /** 登录：校验口令 → 签发 JWT 置 httpOnly Cookie → 返回用户信息。 */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req, HttpServletResponse resp) {
        AppUser user = userRepo.findByUsername(req.username() == null ? "" : req.username().trim()).orElse(null);
        if (user == null || !user.isEnabled() || user.getPasswordHash() == null
                || !passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            return ResponseEntity.status(401).body(Map.of("code", "AUTH_FAILED", "message", "用户名或口令错误"));
        }
        // CR-003（八轮 8-6）：平台侧独立禁用位——源目录有效但平台停用的账号一律拒绝
        if (user.isPlatformDisabled()) {
            return ResponseEntity.status(401).body(Map.of("code", "PLATFORM_DISABLED", "message", "账号已被平台停用，请联系管理员"));
        }
        String token = jwtService.issue(user.getId(), user.getUsername());
        ResponseCookie cookie = ResponseCookie.from(COOKIE, token)
                .httpOnly(true).path("/").sameSite("Lax")
                .maxAge(jwtService.cookieMaxAgeSeconds()).build();
        resp.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return ResponseEntity.ok(userInfo(user));
    }

    /** 登出：清除 Cookie。 */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletResponse resp) {
        ResponseCookie cookie = ResponseCookie.from(COOKIE, "")
                .httpOnly(true).path("/").sameSite("Lax").maxAge(0).build();
        resp.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return ResponseEntity.ok(Map.of("ok", true));
    }

    /** 当前用户（未登录返回 401）。 */
    @GetMapping("/me")
    public ResponseEntity<?> me() {
        String username = CurrentUserContext.get();
        if (username == null) {
            return ResponseEntity.status(401).body(Map.of("code", "UNAUTHENTICATED", "message", "未登录"));
        }
        return userRepo.findByUsername(username)
                .<ResponseEntity<?>>map(u -> ResponseEntity.ok(userInfo(u)))
                .orElse(ResponseEntity.status(401).body(Map.of("code", "UNAUTHENTICATED")));
    }

    private Map<String, Object> userInfo(AppUser u) {
        return Map.of("username", u.getUsername(),
                "displayName", u.getDisplayName() == null ? u.getUsername() : u.getDisplayName(),
                "orgId", u.getOrgId());
    }

    /** 登录请求体。 */
    public record LoginRequest(String username, String password) {
    }
}
