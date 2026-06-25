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

/**
 * 功能级 RBAC·后端强制 HTTP 集成测试（增强③ R3，真实 PG + 登录 + 切面强制 + V27 种子）。验证：
 *  1) 受限用户(cf_user=风险专员)：对未授权写接口(制度新建)→403；对授权写接口(发起评估)→非 403；
 *  2) 超管(group_admin)：对制度新建→非 403。
 *
 * 即"前端隐藏只是体验、后端强制才防绕过"。设计依据：用户增强诉求③ R3。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "grc.scheduler.enabled=false")
@Testcontainers
class RbacEnforcementTest {

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

    private String login(String user) throws Exception {
        HttpResponse<String> r = client.send(HttpRequest.newBuilder(URI.create(base() + "/api/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"" + user + "\",\"password\":\"demo1234\"}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        return r.headers().firstValue("set-cookie").orElseThrow().split(";", 2)[0];
    }

    private int post(String cookie, String path, String json) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(base() + path))
                .header("Content-Type", "application/json").header("Cookie", cookie)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build(), HttpResponse.BodyHandlers.ofString()).statusCode();
    }

    private String base() {
        return "http://localhost:" + port;
    }

    @Test
    void 受限用户_无权写接口403_有权写接口放行() throws Exception {
        String cf = login("cf_user"); // 风险专员：risk RW，policy 无写
        // 未授权：制度新建 → 403
        assertEquals(403, post(cf, "/api/policies",
                "{\"orgId\":13,\"code\":\"P-X\",\"title\":\"t\",\"content\":\"c\"}"));
        // 授权：发起评估(risk.create) → 非 403（业务正常 2xx）
        assertNotEquals(403, post(cf, "/api/assessments",
                "{\"orgId\":13,\"title\":\"风险评估\",\"assessor\":\"u\",\"period\":\"2026Q2\"}"));
    }

    @Test
    void 超管_写接口放行() throws Exception {
        String admin = login("group_admin"); // 平台超管：全 RW
        assertNotEquals(403, post(admin, "/api/policies",
                "{\"orgId\":1,\"code\":\"P-A\",\"title\":\"t\",\"content\":\"c\"}"));
    }
}
