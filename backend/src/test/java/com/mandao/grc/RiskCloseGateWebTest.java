package com.mandao.grc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * CR-002 残余风险关闭门控的【Web 层】端到端验证（真实 PG + 全过滤链 + GlobalExceptionHandler）。
 *
 * 与 {@link RiskAssessmentTest}（service 层、webEnvironment=NONE、手工 set IsolationContext）互补：
 * 本测试走真正的 HTTP 请求，经 {@code IsolationFilter}（读 X-User 头 → 计算 visible_orgs）建立隔离上下文，
 * 再经 {@code RiskFindingController} → Service，校验异常被
 * {@link com.mandao.grc.common.web.GlobalExceptionHandler} 映射成语义化状态码与 JSON。
 *
 * 复用 RiskAssessmentTest 既有的 Testcontainers / PG / Flyway / clean setup，不另起一套。
 * X-User 取 V1 种子用户 {@code pay_user}（org_id=12，故 visible_orgs=[12]），落地 org 12。
 *
 * 用例：
 *  1) 高残余(VERY_HIGH)无接受直接 close → 断言 HTTP 409 且响应体 code=RISK_CLOSE_GATE（红线，核心断言，不得削弱）；
 *  2) 登记风险接受(accept)后再 close → 断言 HTTP 200 且状态变 DONE。
 *
 * 设计依据：CR-002（残余风险关闭门控红线）、D1-2、D2-5（红线端到端可见）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "grc.scheduler.enabled=false")
@AutoConfigureMockMvc
@Testcontainers
class RiskCloseGateWebTest {

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

    /** 走真实过滤链的 HTTP 客户端（含 IsolationFilter）。 */
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /** X-User 头：种子用户 pay_user 属 org 12，落地支付子公司。 */
    private static final String USER = "pay_user";

    /**
     * 每用例前清空 M2 相关表与操作日志（owner 连接，绕 RLS）。与 RiskAssessmentTest 同一清理策略。
     */
    @BeforeEach
    void clean() throws Exception {
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement()) {
            s.executeUpdate("TRUNCATE risk_acceptance, risk_finding, operation_log RESTART IDENTITY CASCADE");
            s.executeUpdate("DELETE FROM assessment WHERE id >= 1000");
        }
    }

    @Test
    void 门控_高残余无接受直接关闭_HTTP返回409且code为RISK_CLOSE_GATE() throws Exception {
        // 造一条评估（用 V1 种子评估 101，归属 org 12）→ 新建高残余风险发现
        Long fid = createFinding(101L, "核心系统弱口令", "VERY_HIGH");
        // 残余设为 VERY_HIGH（高残余）
        setResidual(fid, "VERY_HIGH");

        // 不接受直接 close → 门控拦截：断言 HTTP 409 + code=RISK_CLOSE_GATE
        MvcResult res = mockMvc.perform(post("/api/risk-findings/{id}/close", fid)
                        .header("X-User", USER))
                .andReturn();

        assertEquals(409, res.getResponse().getStatus(),
                "高残余无接受关闭应被门控拦截，返回 409 CONFLICT");
        JsonNode body = objectMapper.readTree(res.getResponse().getContentAsString());
        assertEquals("RISK_CLOSE_GATE", body.path("code").asText(),
                "门控拦截响应体 code 必须为 RISK_CLOSE_GATE（红线端到端可见）");
        // 同时确认 message 字段存在（client.js 读 data.message 展示）
        org.junit.jupiter.api.Assertions.assertFalse(
                body.path("message").asText().isBlank(), "响应体须含非空 message");
    }

    @Test
    void 门控_登记风险接受后再关闭_HTTP返回200且状态变DONE() throws Exception {
        Long fid = createFinding(101L, "数据出境无评估", "VERY_HIGH");
        setResidual(fid, "VERY_HIGH");

        // 登记风险接受（放行凭据）
        mockMvc.perform(post("/api/risk-findings/{id}/accept", fid)
                        .header("X-User", USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approver\":\"ciso\",\"reason\":\"残余可接受，纳入持续监控\"}"))
                .andReturn();

        // 放行后 close → 断言 200 且状态 DONE
        MvcResult res = mockMvc.perform(post("/api/risk-findings/{id}/close", fid)
                        .header("X-User", USER))
                .andReturn();

        assertEquals(200, res.getResponse().getStatus(), "已登记接受后关闭应放行，返回 200");
        JsonNode body = objectMapper.readTree(res.getResponse().getContentAsString());
        assertEquals("DONE", body.path("status").asText(), "关闭后状态应为 DONE");

        // 再次确认通过 GET 也是 DONE
        MvcResult got = mockMvc.perform(get("/api/risk-findings/{id}", fid)
                        .header("X-User", USER))
                .andReturn();
        assertEquals(200, got.getResponse().getStatus());
        assertEquals("DONE",
                objectMapper.readTree(got.getResponse().getContentAsString()).path("status").asText());
    }

    // ---------- 测试辅助：经 HTTP 造数据 ----------

    /** POST /api/risk-findings 新建风险发现，返回其 id。 */
    private Long createFinding(Long assessmentId, String title, String inherentLevel) throws Exception {
        String json = String.format(
                "{\"orgId\":12,\"assessmentId\":%d,\"title\":\"%s\",\"inherentLevel\":\"%s\"}",
                assessmentId, title, inherentLevel);
        MvcResult res = mockMvc.perform(post("/api/risk-findings")
                        .header("X-User", USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andReturn();
        assertEquals(200, res.getResponse().getStatus(), "新建风险发现应成功");
        return objectMapper.readTree(res.getResponse().getContentAsString()).path("id").asLong();
    }

    /** POST /api/risk-findings/{id}/residual 评估残余等级。 */
    private void setResidual(Long fid, String residualLevel) throws Exception {
        mockMvc.perform(post("/api/risk-findings/{id}/residual", fid)
                        .header("X-User", USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"residualLevel\":\"" + residualLevel + "\"}"))
                .andReturn();
    }
}
