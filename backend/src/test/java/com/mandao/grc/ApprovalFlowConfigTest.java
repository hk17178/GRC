package com.mandao.grc;

import com.mandao.grc.common.isolation.IsolationContext;
import com.mandao.grc.modules.workflow.ApprovalBizType;
import com.mandao.grc.modules.workflow.ApprovalFlow;
import com.mandao.grc.modules.workflow.ApprovalFlowService;
import com.mandao.grc.modules.workflow.ApproverType;
import com.mandao.grc.modules.workflow.CountersignMode;
import com.mandao.grc.modules.workflow.FlowGraph;
import com.mandao.grc.modules.workflow.FlowValidationException;
import com.mandao.grc.modules.workflow.NodeType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 可配置审批流·配置层集成测试（P1.1：真实 PG + 切面 + RLS）。验证：
 *  1) 草稿增改查；2) 合法画布校验通过、非法画布(审批节点无审批人)被 400 拦截；
 *  3) 组织隔离：org12 的审批流，org13 列不到。
 *
 * 设计依据：用户增强诉求②（审批流可配置）、D2-5。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "grc.scheduler.enabled=false")
@Testcontainers
class ApprovalFlowConfigTest {

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

    @Autowired
    private ApprovalFlowService service;

    private static final long ORG_PAY = 12L;
    private static final long ORG_CF = 13L;

    @BeforeEach
    void clean() throws Exception {
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement()) {
            s.executeUpdate("TRUNCATE approval_flow, approval_instance, approval_task_log RESTART IDENTITY CASCADE");
        }
    }

    @AfterEach
    void clearContext() {
        IsolationContext.clear();
    }

    /** 合法画布：开始 → 或签审批(CHECKER) → 结束(APPROVED)。 */
    private FlowGraph validGraph() {
        return new FlowGraph(
                List.of(
                        new FlowGraph.FlowNode("start", NodeType.START, null, null, null, null, null, null, null, null),
                        new FlowGraph.FlowNode("n1", NodeType.APPROVAL, "部门审批", ApproverType.ROLE,
                                List.of("CHECKER"), CountersignMode.ANY, 1, null, null, null),
                        new FlowGraph.FlowNode("end", NodeType.END, null, null, null, null, null, null, null, "APPROVED")),
                List.of(new FlowGraph.FlowEdge("start", "n1", null),
                        new FlowGraph.FlowEdge("n1", "end", null)));
    }

    /** 非法画布：审批节点缺审批人与会签/或签模式。 */
    private FlowGraph invalidGraph() {
        return new FlowGraph(
                List.of(
                        new FlowGraph.FlowNode("start", NodeType.START, null, null, null, null, null, null, null, null),
                        new FlowGraph.FlowNode("n1", NodeType.APPROVAL, "审批", null, null, null, null, null, null, null),
                        new FlowGraph.FlowNode("end", NodeType.END, null, null, null, null, null, null, null, "APPROVED")),
                List.of(new FlowGraph.FlowEdge("start", "n1", null),
                        new FlowGraph.FlowEdge("n1", "end", null)));
    }

    @Test
    void 草稿增改查_与合法校验通过() {
        Long id = asOrg(ORG_PAY, () -> service.createDraft(ORG_PAY, ApprovalBizType.POLICY_PUBLISH, "制度发布默认流", validGraph()).getId());
        assertEquals(1, asOrg(ORG_PAY, () -> service.list(ApprovalBizType.POLICY_PUBLISH)).size());
        asOrg(ORG_PAY, () -> service.updateDraft(id, "制度发布流v2", validGraph()));
        assertEquals("制度发布流v2", asOrg(ORG_PAY, () -> service.get(id).getName()));
        // 合法画布校验不抛异常
        asOrg(ORG_PAY, () -> { service.validate(id); return null; });
    }

    @Test
    void 非法画布_审批节点无审批人_被校验拦截() {
        Long id = asOrg(ORG_PAY, () -> service.createDraft(ORG_PAY, ApprovalBizType.RISK_ACCEPT, "坏流程", invalidGraph()).getId());
        FlowValidationException ex = assertThrows(FlowValidationException.class,
                () -> { IsolationContext.set(List.of(ORG_PAY)); try { service.validate(id); } finally { IsolationContext.clear(); } });
        assertTrue(ex.getMessage().contains("审批人"), "应提示审批人缺失，实际：" + ex.getMessage());
    }

    @Test
    void 组织隔离_org12审批流org13看不到() {
        asOrg(ORG_PAY, () -> service.createDraft(ORG_PAY, ApprovalBizType.SOD_EXCEPTION, "仅支付", validGraph()));
        assertEquals(1, asOrg(ORG_PAY, () -> service.list(null)).size());
        assertTrue(asOrg(ORG_CF, () -> service.list(null)).isEmpty(), "org13 不应看到 org12 的审批流");
    }

    private <T> T asOrg(long orgId, Supplier<T> action) {
        IsolationContext.set(List.of(orgId));
        try {
            return action.get();
        } finally {
            IsolationContext.clear();
        }
    }
}
