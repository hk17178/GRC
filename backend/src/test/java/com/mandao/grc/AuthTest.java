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
    void X_User回退仍生效() throws Exception {
        HttpResponse<String> me = get("/api/auth/me", "X-User", "pay_user");
        assertEquals(200, me.statusCode());
        assertTrue(me.body().contains("pay_user"), "X-User 回退应取到用户：" + me.body());
    }
}
