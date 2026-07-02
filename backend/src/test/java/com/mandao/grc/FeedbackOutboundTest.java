package com.mandao.grc;

import com.mandao.grc.common.isolation.IsolationContext;
import com.mandao.grc.modules.feedback.Feedback;
import com.mandao.grc.modules.feedback.FeedbackService;
import com.mandao.grc.modules.feedback.FeedbackType;
import com.mandao.grc.modules.workflow.ApprovalDecision;
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

/**
 * 反馈出站审批集成测试（V43，真实 PG + Flowable）。验证：
 *  1) 已办结反馈发起出站回复 → PENDING_APPROVAL（Flowable 建审批任务）→ 批准 → APPROVED；
 *  2) 驳回后可改稿重发（REJECTED → 再次发起）；
 *  3) 门控：未办结反馈发起出站被拒；审批中重复发起被拒。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "grc.scheduler.enabled=false")
@Testcontainers
class FeedbackOutboundTest {

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
    private FeedbackService service;

    private static final long ORG_PAY = 12L;

    @BeforeEach
    void clean() throws Exception {
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement()) {
            // 不 RESTART IDENTITY：Flowable 流程实例跨测试残留，businessKey=FEEDBACK_OUTBOUND:{id}
            // 若 id 复位会撞出"同 key 双实例"，故保持 id 全局递增。
            s.executeUpdate("TRUNCATE feedback, operation_log CASCADE");
        }
    }

    @AfterEach
    void clearContext() {
        IsolationContext.clear();
    }

    /** 建一条已办结反馈。 */
    private Feedback resolvedFeedback() {
        Feedback f = asOrg(ORG_PAY, () ->
                service.submit(ORG_PAY, FeedbackType.SUGGESTION, "对外咨询回复", "内容", "u", "u"));
        asOrg(ORG_PAY, () -> service.triage(f.getId(), "handler", "c"));
        return asOrg(ORG_PAY, () -> service.resolve(f.getId(), "已核实并给出结论", "handler"));
    }

    @Test
    void 出站审批_批准链路() {
        Feedback f = resolvedFeedback();
        Feedback pending = asOrg(ORG_PAY, () -> service.submitOutbound(f.getId(), "尊敬的来函人：…", "handler"));
        assertEquals("PENDING_APPROVAL", pending.getOutboundStatus());

        // 审批中重复发起被拒
        assertThrows(IllegalStateException.class,
                () -> asOrg(ORG_PAY, () -> service.submitOutbound(f.getId(), "再来一稿", "handler")));

        Feedback approved = asOrg(ORG_PAY, () ->
                service.decideOutbound(f.getId(), ApprovalDecision.APPROVED, "compliance_lead", null));
        assertEquals("APPROVED", approved.getOutboundStatus(), "批准后可对外发送");
        assertEquals("尊敬的来函人：…", approved.getOutboundReply(), "回复稿保留");
    }

    @Test
    void 出站审批_驳回可改稿重发() {
        Feedback f = resolvedFeedback();
        asOrg(ORG_PAY, () -> service.submitOutbound(f.getId(), "初稿", "handler"));
        Feedback rejected = asOrg(ORG_PAY, () ->
                service.decideOutbound(f.getId(), ApprovalDecision.REJECTED, "compliance_lead", "口径不妥"));
        assertEquals("REJECTED", rejected.getOutboundStatus());

        // 改稿重发 → 再次进入审批
        Feedback again = asOrg(ORG_PAY, () -> service.submitOutbound(f.getId(), "修订稿", "handler"));
        assertEquals("PENDING_APPROVAL", again.getOutboundStatus());
        assertEquals("修订稿", again.getOutboundReply());
    }

    @Test
    void 未办结反馈_不可发起出站() {
        Feedback f = asOrg(ORG_PAY, () ->
                service.submit(ORG_PAY, FeedbackType.QUESTION, "进行中", "内容", "u", "u"));
        assertThrows(IllegalStateException.class,
                () -> asOrg(ORG_PAY, () -> service.submitOutbound(f.getId(), "稿", "u")),
                "未办结不允许出站");
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
