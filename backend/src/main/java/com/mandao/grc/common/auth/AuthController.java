package com.mandao.grc.common.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 认证端点：/api/auth（登录/登出/当前用户/改密）。
 *
 * 登录成功签发 JWT，置于 <b>httpOnly Cookie</b>（grc_token，抗 XSS——JS 读不到）；浏览器后续请求自动携带，
 * 由认证过滤器校验并建立隔离/权限上下文。Cookie SameSite=Lax 缓解 CSRF。密钥仅环境注入。
 *
 * 安全加固包（B15/B17）：
 *  - 登录审计：成功/失败/锁定拒绝全落 login_audit（等保三级测评必查）；
 *  - 失败锁定：连续失败 5 次锁 15 分钟（锁定期间即便口令正确也拒绝）；
 *  - 首登改密：must_change_password=true 的账号登录成功后前端强制走改密；
 *  - Cookie Secure：grc.auth.cookie-secure=true（生产 HTTPS 环境必开）。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /** 令牌 Cookie 名。 */
    public static final String COOKIE = "grc_token";

    /** 失败锁定阈值与时长（等保口径；后续可参数化到 system_setting）。 */
    private static final int LOCK_THRESHOLD = 5;
    private static final int LOCK_MINUTES = 15;

    private final AppUserRepository userRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginAuditRepository loginAuditRepo;

    /** 生产 HTTPS 环境置 true（B17：Cookie Secure 配置化，不再只靠人工清单）。 */
    private final boolean cookieSecure;

    /** L-10：是否信任 X-Forwarded-For（仅当部署在可信反向代理之后才置 true，否则该头可被客户端伪造）。 */
    private final boolean trustForwarded;

    public AuthController(AppUserRepository userRepo, PasswordEncoder passwordEncoder, JwtService jwtService,
                          LoginAuditRepository loginAuditRepo,
                          @Value("${grc.auth.cookie-secure:false}") boolean cookieSecure,
                          @Value("${grc.auth.trust-forwarded-header:false}") boolean trustForwarded) {
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.loginAuditRepo = loginAuditRepo;
        this.cookieSecure = cookieSecure;
        this.trustForwarded = trustForwarded;
    }

    /** 登录：锁定检查 → 校验口令（失败累计/锁定） → 签发 JWT 置 httpOnly Cookie；全程审计。 */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req, HttpServletRequest httpReq,
                                   HttpServletResponse resp) {
        String username = req.username() == null ? "" : req.username().trim();
        String ip = clientIp(httpReq);
        AppUser user = userRepo.findByUsername(username).orElse(null);

        // 锁定检查先于口令校验：锁定期内即便口令正确也拒绝（防持续爆破）
        if (user != null && user.getLockedUntil() != null && user.getLockedUntil().isAfter(OffsetDateTime.now())) {
            audit(username, false, "LOCKED", ip);
            long left = java.time.Duration.between(OffsetDateTime.now(), user.getLockedUntil()).toMinutes() + 1;
            return ResponseEntity.status(401).body(Map.of("code", "LOCKED",
                    "message", "连续失败次数过多，账号已锁定，请约 " + left + " 分钟后重试"));
        }

        if (user == null || !user.isEnabled() || user.getPasswordHash() == null
                || !passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            if (user != null) {
                user.recordLoginFailure(LOCK_THRESHOLD, LOCK_MINUTES);
                userRepo.save(user);
            }
            audit(username, false, "BAD_CREDENTIAL", ip);
            return ResponseEntity.status(401).body(Map.of("code", "AUTH_FAILED", "message", "用户名或口令错误"));
        }
        // CR-003（八轮 8-6）：平台侧独立禁用位——源目录有效但平台停用的账号一律拒绝
        if (user.isPlatformDisabled()) {
            audit(username, false, "PLATFORM_DISABLED", ip);
            return ResponseEntity.status(401).body(Map.of("code", "PLATFORM_DISABLED", "message", "账号已被平台停用，请联系管理员"));
        }

        user.recordLoginSuccess();
        userRepo.save(user);
        audit(username, true, "OK", ip);

        String token = jwtService.issue(user.getId(), user.getUsername(), user.isMustChangePassword(),
                user.getTokenEpoch());
        resp.addHeader(HttpHeaders.SET_COOKIE, buildCookie(token, jwtService.cookieMaxAgeSeconds()).toString());
        return ResponseEntity.ok(userInfo(user));
    }

    /** 登出：清除 Cookie。 */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletResponse resp) {
        // M-15：登出即吊销该用户全部既发令牌（bump token_epoch），不再"登出后旧 JWT 仍有效 12h"
        String username = CurrentUserContext.get();
        if (username != null) {
            userRepo.findByUsername(username).ifPresent(u -> {
                u.bumpTokenEpoch();
                userRepo.save(u);
            });
        }
        resp.addHeader(HttpHeaders.SET_COOKIE, buildCookie("", 0).toString());
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

    /**
     * 修改口令（B17 首登强制改密的落点；也供日常改密）。
     * 规则：须已登录；旧口令必须正确；新口令 ≥8 位且不得为演示口令/与旧口令相同。
     */
    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestBody ChangePasswordRequest req, HttpServletRequest httpReq,
                                            HttpServletResponse resp) {
        String username = CurrentUserContext.get();
        if (username == null) {
            return ResponseEntity.status(401).body(Map.of("code", "UNAUTHENTICATED", "message", "未登录"));
        }
        AppUser user = userRepo.findByUsername(username).orElse(null);
        if (user == null || !passwordEncoder.matches(req.oldPassword(), user.getPasswordHash())) {
            audit(username, false, "PWD_CHANGE_BAD_OLD", clientIp(httpReq));
            return ResponseEntity.status(401).body(Map.of("code", "AUTH_FAILED", "message", "原口令错误"));
        }
        String np = req.newPassword() == null ? "" : req.newPassword();
        if (isWeakPassword(np, req.oldPassword())) {   // L-11
            return ResponseEntity.badRequest().body(Map.of("code", "WEAK_PASSWORD",
                    "message", "新口令须至少 10 位、含大写/小写/数字/符号中至少三类，且不得为常见弱口令或与旧口令相同"));
        }
        user.changePassword(passwordEncoder.encode(np));   // 内部 bump token_epoch（M-15：使旧会话失效）
        userRepo.save(user);
        // M-14/M-15：改密后重签发 mc=false + 新 epoch 令牌，解除首登限制并顶掉旧令牌
        String token = jwtService.issue(user.getId(), user.getUsername(), false, user.getTokenEpoch());
        resp.addHeader(HttpHeaders.SET_COOKIE, buildCookie(token, jwtService.cookieMaxAgeSeconds()).toString());
        audit(username, true, "PWD_CHANGED", clientIp(httpReq));
        return ResponseEntity.ok(Map.of("ok", true));
    }

    /** L-11：口令强度——≥10 位、含大写/小写/数字/符号至少三类、非常见弱口令、不与旧口令相同。 */
    private static final java.util.Set<String> WEAK_BLOCKLIST = java.util.Set.of(
            "demo1234", "password", "12345678", "admin123", "qwerty123", "abcd1234", "password1", "1234567890");

    private static boolean isWeakPassword(String np, String old) {
        if (np == null || np.length() < 10 || np.equals(old) || WEAK_BLOCKLIST.contains(np.toLowerCase())) {
            return true;
        }
        int classes = 0;
        if (np.matches(".*[a-z].*")) classes++;
        if (np.matches(".*[A-Z].*")) classes++;
        if (np.matches(".*\\d.*")) classes++;
        if (np.matches(".*[^a-zA-Z0-9].*")) classes++;
        return classes < 3;
    }

    private ResponseCookie buildCookie(String token, long maxAge) {
        return ResponseCookie.from(COOKIE, token)
                .httpOnly(true).path("/").sameSite("Lax").secure(cookieSecure)
                .maxAge(maxAge).build();
    }

    private void audit(String username, boolean success, String reason, String ip) {
        try {
            loginAuditRepo.save(new LoginAudit(username, success, reason, ip));
        } catch (RuntimeException e) {
            // 审计落库失败不阻断认证主流程（审计表异常单独排查）
        }
    }

    private String clientIp(HttpServletRequest req) {
        // L-10：X-Forwarded-For 可被客户端伪造；默认取 TCP 对端地址，仅可信反代后（配置开启）才取 XFF 首跳
        if (trustForwarded) {
            String xff = req.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                return xff.split(",")[0].trim();
            }
        }
        return req.getRemoteAddr();
    }

    private Map<String, Object> userInfo(AppUser u) {
        return Map.of("username", u.getUsername(),
                "displayName", u.getDisplayName() == null ? u.getUsername() : u.getDisplayName(),
                "orgId", u.getOrgId(),
                "mustChangePassword", u.isMustChangePassword());
    }

    /** 登录请求体。 */
    public record LoginRequest(String username, String password) {
    }

    /** 改密请求体。 */
    public record ChangePasswordRequest(String oldPassword, String newPassword) {
    }
}
