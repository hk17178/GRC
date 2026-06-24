package com.mandao.grc;

import com.mandao.grc.common.isolation.IsolationContext;
import com.mandao.grc.modules.audit.ChainVerifyResult;
import com.mandao.grc.modules.audit.HashChainService;
import com.mandao.grc.modules.feedback.FeedbackService;
import com.mandao.grc.modules.feedback.FeedbackStatus;
import com.mandao.grc.modules.feedback.FeedbackType;
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
import java.util.concurrent.Callable;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 建议与反馈集成测试（真实 PG + 切面 + RLS）。验证：
 *  1) 办结闭环红线：无处置结果不得办结；受理→办结→关闭全程；
 *  2) 状态机：驳回后不可再办结；
 *  3) 组织隔离：org12 反馈，org13 看不到；
 *  4) 留痕：提交/受理/办结哈希链校验通过且计数正确。
 *
 * 设计依据：需求 CR-004（反馈建议）、D2-5。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "grc.scheduler.enabled=false")
@Testcontainers
class FeedbackTest {

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
    private FeedbackService feedbackService;

    @Autowired
    private HashChainService hashChainService;

    private static final long ORG_PAY = 12L;
    private static final long ORG_CF = 13L;

    @BeforeEach
    void clean() throws Exception {
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement()) {
            s.executeUpdate("TRUNCATE feedback, operation_log RESTART IDENTITY CASCADE");
        }
    }

    @AfterEach
    void clearContext() {
        IsolationContext.clear();
    }

    @Test
    void 办结闭环_无处置结果不得办结_全程通过() {
        Long id = asOrg(ORG_PAY, () -> feedbackService.submit(ORG_PAY, FeedbackType.SUGGESTION,
                "增加批量导出", "希望支持台账批量导出 Excel", "alice", "alice").getId());

        assertEquals(FeedbackStatus.IN_PROGRESS, asOrg(ORG_PAY, () -> feedbackService.triage(id, "pm", "lead").getStatus()));

        // 红线：无处置结果办结被拒
        assertThrows(IllegalArgumentException.class,
                () -> runAsOrg(ORG_PAY, () -> feedbackService.resolve(id, "  ", "pm")));

        // 有处置结果可办结 → 关闭
        assertEquals(FeedbackStatus.RESOLVED,
                asOrg(ORG_PAY, () -> feedbackService.resolve(id, "已纳入 v1.1 排期", "pm").getStatus()));
        assertEquals(FeedbackStatus.CLOSED, asOrg(ORG_PAY, () -> feedbackService.close(id, "lead").getStatus()));
    }

    @Test
    void 状态机_驳回后不可再办结() {
        Long id = asOrg(ORG_PAY, () -> feedbackService.submit(ORG_PAY, FeedbackType.COMPLAINT, "X", "Y", "u", "u").getId());
        asOrg(ORG_PAY, () -> feedbackService.reject(id, "重复反馈", "pm"));
        assertThrows(IllegalStateException.class,
                () -> runAsOrg(ORG_PAY, () -> feedbackService.resolve(id, "x", "pm")));
    }

    @Test
    void 组织隔离_org12反馈org13看不到() {
        asOrg(ORG_PAY, () -> feedbackService.submit(ORG_PAY, FeedbackType.BUG, "仅支付可见", "x", "u", "u"));
        assertEquals(1, asOrg(ORG_PAY, () -> feedbackService.list()).size(), "org12 应看到自己的 1 条反馈");
        assertTrue(asOrg(ORG_CF, () -> feedbackService.list()).isEmpty(), "org13 不应看到 org12 的反馈");
    }

    @Test
    void 留痕_提交受理办结共3条() {
        Long id = asOrg(ORG_PAY, () -> feedbackService.submit(ORG_PAY, FeedbackType.QUESTION, "H", "h", "u", "u").getId()); // SUBMIT
        asOrg(ORG_PAY, () -> feedbackService.triage(id, "pm", "lead"));                                                       // TRIAGE
        asOrg(ORG_PAY, () -> feedbackService.resolve(id, "已答复", "pm"));                                                    // RESOLVE

        ChainVerifyResult r = asOrg(ORG_PAY, () -> hashChainService.verify(ORG_PAY));
        assertTrue(r.valid(), "留痕后链应校验通过");
        assertEquals(3, r.count(), "应有 3 条反馈留痕");
    }

    // ---------- 测试辅助 ----------

    private <T> T asOrg(long orgId, Supplier<T> action) {
        IsolationContext.set(List.of(orgId));
        try {
            return action.get();
        } finally {
            IsolationContext.clear();
        }
    }

    private void runAsOrg(long orgId, Callable<?> action) throws Exception {
        IsolationContext.set(List.of(orgId));
        try {
            action.call();
        } finally {
            IsolationContext.clear();
        }
    }
}
