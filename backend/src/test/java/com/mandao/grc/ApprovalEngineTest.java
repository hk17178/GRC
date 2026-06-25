package com.mandao.grc;

import com.mandao.grc.common.isolation.IsolationContext;
import com.mandao.grc.modules.audit.ChainVerifyResult;
import com.mandao.grc.modules.audit.HashChainService;
import com.mandao.grc.modules.workflow.ApprovalBizType;
import com.mandao.grc.modules.workflow.ApprovalDecision;
import com.mandao.grc.modules.workflow.ApprovalEngine;
import com.mandao.grc.modules.workflow.ApprovalFlowService;
import com.mandao.grc.modules.workflow.ApprovalInstance;
import com.mandao.grc.modules.workflow.InstanceStatus;
import org.flowable.task.api.Task;
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
 * 可配置审批·运行时引擎集成测试（P1.3：真实 PG + Flowable + 切面 + RLS）。验证：
 *  1) 灌默认流 → 发起 → 处置 → 实例 APPROVED、决定流水 1 条、哈希链(发起+处置)校验通过；
 *  2) 未配置生效流程时发起被拒。
 *
 * 设计依据：用户增强诉求②（审批流可配置）、CR-001/CR-003、D2-5。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "grc.scheduler.enabled=false")
@Testcontainers
class ApprovalEngineTest {

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

    @Autowired private ApprovalFlowService flowService;
    @Autowired private ApprovalEngine engine;
    @Autowired private HashChainService hashChainService;

    private static final long ORG_PAY = 12L;

    @BeforeEach
    void clean() throws Exception {
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement()) {
            s.executeUpdate("TRUNCATE approval_flow, approval_instance, approval_task_log, operation_log RESTART IDENTITY CASCADE");
        }
    }

    @AfterEach
    void clearContext() {
        IsolationContext.clear();
    }

    @Test
    void 灌默认流_发起处置_通过_留痕() {
        // 灌默认单节点流（CHECKER 审批）并发布
        asOrg(ORG_PAY, () -> flowService.seedDefault(ORG_PAY, ApprovalBizType.POLICY_PUBLISH, "CHECKER"));

        // 发起审批
        ApprovalInstance inst = asOrg(ORG_PAY, () -> engine.submit(ApprovalBizType.POLICY_PUBLISH, 1L, ORG_PAY, "alice"));
        assertEquals(InstanceStatus.RUNNING, inst.getStatus());

        // 取待办任务并通过
        List<Task> tasks = asOrg(ORG_PAY, () -> engine.activeTasks(ApprovalBizType.POLICY_PUBLISH, 1L));
        assertEquals(1, tasks.size(), "默认单节点应有 1 个审批任务");
        String taskId = tasks.get(0).getId();
        ApprovalInstance done = asOrg(ORG_PAY, () -> engine.decide(taskId, ApprovalDecision.APPROVED, "bob", "同意"));
        assertEquals(InstanceStatus.APPROVED, done.getStatus(), "处置通过后实例应 APPROVED");

        // 决定流水 1 条
        assertEquals(1, asOrg(ORG_PAY, () -> engine.logs(done.getId())).size());

        // 哈希链：发起 + 处置 共 2 条，校验通过
        ChainVerifyResult r = asOrg(ORG_PAY, () -> hashChainService.verify(ORG_PAY));
        assertTrue(r.valid(), "审批留痕链应校验通过");
        assertEquals(2, r.count(), "应有 发起+处置 2 条留痕");
    }

    @Test
    void 未配置生效流程_发起被拒() {
        assertThrows(IllegalStateException.class,
                () -> { IsolationContext.set(List.of(ORG_PAY));
                        try { engine.submit(ApprovalBizType.RISK_ACCEPT, 9L, ORG_PAY, "alice"); }
                        finally { IsolationContext.clear(); } });
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
