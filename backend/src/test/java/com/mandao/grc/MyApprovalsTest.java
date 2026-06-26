package com.mandao.grc;

import com.mandao.grc.common.auth.CurrentUserContext;
import com.mandao.grc.common.isolation.IsolationContext;
import com.mandao.grc.modules.workbench.MyApprovalItem;
import com.mandao.grc.modules.workbench.WorkbenchService;
import com.mandao.grc.modules.workflow.ApprovalBizType;
import com.mandao.grc.modules.workflow.ApprovalEngine;
import com.mandao.grc.modules.workflow.ApprovalFlowService;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 我的审批待办·按登录人过滤集成测试（真实 PG + Flowable + V27 角色种子）。验证：
 *  发起一条候选组=RISK_OFFICER 的审批任务后——持该角色的 cf_user 能在"我的审批"看到它，
 *  不持该角色的 pay_user 看不到。即真正"按登录人(其角色)"而非"按组织"过滤。
 *
 * 设计依据：Phase D 收尾「我的」按登录人过滤（auth 解锁）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "grc.scheduler.enabled=false")
@Testcontainers
class MyApprovalsTest {

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
    @Autowired private WorkbenchService workbench;

    private static final long ORG_CF = 13L;

    @BeforeEach
    void clean() throws Exception {
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement()) {
            s.executeUpdate("TRUNCATE approval_flow, approval_instance, approval_task_log RESTART IDENTITY CASCADE");
        }
    }

    @AfterEach
    void clearCtx() {
        IsolationContext.clear();
        CurrentUserContext.clear();
    }

    @Test
    void 我的审批待办_按角色匹配登录人() {
        IsolationContext.set(List.of(ORG_CF));
        try {
            // 灌一条审批人=RISK_OFFICER 的默认流并发起 → 产生候选组 RISK_OFFICER 的待办任务
            flowService.seedDefault(ORG_CF, ApprovalBizType.POLICY_PUBLISH, "RISK_OFFICER");
            engine.submit(ApprovalBizType.POLICY_PUBLISH, 1L, ORG_CF, "alice");

            // cf_user 持 RISK_OFFICER → 应看到这条"分给我"的审批
            CurrentUserContext.set("cf_user");
            List<MyApprovalItem> mine = workbench.myApprovals();
            assertEquals(1, mine.size(), "cf_user 应看到 1 条我的审批");
            assertEquals("RISK_OFFICER", mine.get(0).roleGroup());
            assertEquals("POLICY_PUBLISH", mine.get(0).bizType());
            assertEquals(1L, mine.get(0).bizId());

            // pay_user 不持 RISK_OFFICER → 看不到
            CurrentUserContext.set("pay_user");
            assertTrue(workbench.myApprovals().isEmpty(), "pay_user 不应看到该审批");
        } finally {
            IsolationContext.clear();
            CurrentUserContext.clear();
        }
    }
}
