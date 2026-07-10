package com.mandao.grc;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 认证地基 HTTP 集成测试（增强③ R1：真实 PG + 登录/JWT/Cookie/过滤器）。验证：
 *  1) 正确口令登录 → 200 + 下发 httpOnly Cookie grc_token；带该 Cookie 访问 /me 得当前用户；
 *  2) 错误口令 → 401；
 *  3) X-User 回退仍生效（保证既有测试/开发零改动）。
 *
 * 用 JDK java.net.http.HttpClient（不带 HttpURLConnection 对 401 的 auth-retry 怪癖）。
 * 设计依据：用户增强诉求③（功能级 RBAC）R1 认证地基。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "grc.scheduler.enabled=false")
@Testcontainers
class AuthTest {

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
            .withDatabaseName("grc")
            .withUsername("grc_owner")
            .withPassword("owner_pw")
            .withInitScript("testcontainers-init.sql");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PG::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "grc_app");
        registry.add("spring.datasource.password", () -> "grc_app_pw");
        registry.add("spring.flyway.url", PG::getJdbcUrl);
        registry.add("spring.flyway.user", () -> "grc_owner");
        registry.add("spring.flyway.password", () -> "owner_pw");
    }

    @LocalServerPort
    int port;

    private final HttpClient client = HttpClient.newHttpClient();

    private String base() {
        return "http://localhost:" + port;
    }

    private HttpResponse<String> post(String path, String json, String... headers) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base() + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json));
        for (int i = 0; i + 1 < headers.length; i += 2) {
            b.header(headers[i], headers[i + 1]);
        }
        return client.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path, String... headers) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base() + path)).GET();
        for (int i = 0; i + 1 < headers.length; i += 2) {
            b.header(headers[i], headers[i + 1]);
        }
        return client.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void 正确口令登录_下发Cookie_带Cookie取当前用户() throws Exception {
        HttpResponse<String> login = post("/api/auth/login", "{\"username\":\"group_admin\",\"password\":\"demo1234\"}");
        assertEquals(200, login.statusCode());
        assertTrue(login.body().contains("group_admin"), "登录响应应含用户名");
        String setCookie = login.headers().firstValue("set-cookie").orElse("");
        assertTrue(setCookie.contains("grc_token=") && setCookie.toLowerCase().contains("httponly"),
                "应下发 httpOnly grc_token：" + setCookie);

        String cookie = setCookie.split(";", 2)[0]; // grc_token=...
        HttpResponse<String> me = get("/api/auth/me", "Cookie", cookie);
        assertEquals(200, me.statusCode());
        assertTrue(me.body().contains("group_admin") && me.body().contains("集团管理员"),
                "带 Cookie 应取到当前用户：" + me.body());
    }

    @Test
    void 错误口令_401() throws Exception {
        HttpResponse<String> login = post("/api/auth/login", "{\"username\":\"group_admin\",\"password\":\"wrong\"}");
        assertEquals(401, login.statusCode());
    }

    @Test
    void p0_2_未认证业务端点401_公开端点放行() throws Exception {
        // 全局认证前置：未认证访问业务端点一律 401（不再进业务、不再靠 RLS 兜底）
        assertEquals(401, get("/api/settings").statusCode(), "未认证 /api/settings 应被网关 401");
        assertEquals(401, get("/api/policies").statusCode(), "未认证 /api/policies 应被网关 401");
        // 登录前必达的公开端点：品牌读取放行（非 401）
        assertNotEquals(401, get("/api/branding").statusCode(), "GET /api/branding 应公开可读");
    }

    @Test
    void X_User回退仍生效() throws Exception {
        HttpResponse<String> me = get("/api/auth/me", "X-User", "pay_user");
        assertEquals(200, me.statusCode());
        assertTrue(me.body().contains("pay_user"), "X-User 回退应取到用户：" + me.body());
    }

    // ===== 安全加固包 =====

    @Test
    void 连续失败5次_账号锁定_正确口令也被拒() throws Exception {
        // 用 cf_user（避免污染其它用例常用的 group_admin/pay_user）
        for (int i = 0; i < 5; i++) {
            HttpResponse<String> bad = post("/api/auth/login",
                    "{\"username\":\"cf_user\",\"password\":\"wrong" + i + "\"}");
            assertEquals(401, bad.statusCode());
        }
        // 第 6 次即便口令正确也应被锁定拒绝，且 code=LOCKED
        HttpResponse<String> locked = post("/api/auth/login",
                "{\"username\":\"cf_user\",\"password\":\"demo1234\"}");
        assertEquals(401, locked.statusCode());
        assertTrue(locked.body().contains("LOCKED"), "连续失败后应锁定：" + locked.body());
    }

    @Test
    void 登录审计_成功与失败均落台账() throws Exception {
        post("/api/auth/login", "{\"username\":\"group_admin\",\"password\":\"demo1234\"}");
        post("/api/auth/login", "{\"username\":\"group_admin\",\"password\":\"nope\"}");
        try (java.sql.Connection owner = java.sql.DriverManager.getConnection(
                PG.getJdbcUrl(), "grc_owner", "owner_pw");
             java.sql.Statement s = owner.createStatement();
             java.sql.ResultSet rs = s.executeQuery(
                     "SELECT count(*) FILTER (WHERE success), count(*) FILTER (WHERE NOT success) "
                             + "FROM login_audit WHERE username = 'group_admin'")) {
            rs.next();
            assertTrue(rs.getInt(1) >= 1, "应有成功登录审计");
            assertTrue(rs.getInt(2) >= 1, "应有失败登录审计");
        }
    }

    @Test
    void 改密_弱口令被拒_正常改密后新口令可登录() throws Exception {
        // 用登录 Cookie 走改密（pay_user）
        HttpResponse<String> login = post("/api/auth/login",
                "{\"username\":\"pay_user\",\"password\":\"demo1234\"}");
        assertEquals(200, login.statusCode());
        String cookie = login.headers().firstValue("set-cookie").orElse("").split(";", 2)[0];

        // 弱口令（<8 位）→ 400
        HttpResponse<String> weak = postWithCookie("/api/auth/change-password", cookie,
                "{\"oldPassword\":\"demo1234\",\"newPassword\":\"short\"}");
        assertEquals(400, weak.statusCode());

        // 正常改密 → 200；旧口令再登录失败、新口令登录成功
        HttpResponse<String> ok = postWithCookie("/api/auth/change-password", cookie,
                "{\"oldPassword\":\"demo1234\",\"newPassword\":\"NewPass2026\"}");
        assertEquals(200, ok.statusCode());
        assertEquals(401, post("/api/auth/login",
                "{\"username\":\"pay_user\",\"password\":\"demo1234\"}").statusCode());
        assertEquals(200, post("/api/auth/login",
                "{\"username\":\"pay_user\",\"password\":\"NewPass2026\"}").statusCode());
    }

    private HttpResponse<String> postWithCookie(String path, String cookie, String json) throws Exception {
        return post(path, json, "Cookie", cookie);
    }
}
