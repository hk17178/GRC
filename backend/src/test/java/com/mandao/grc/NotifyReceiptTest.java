package com.mandao.grc;

import com.mandao.grc.common.isolation.IsolationContext;
import com.mandao.grc.modules.workbench.NotificationView;
import com.mandao.grc.modules.workbench.WorkbenchService;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 通知回执/降噪/定期简报集成测试（V41）。验证：
 *  1) 降噪：同一 (对象,事件) 三条不同阈值的提醒合并为 1 条展示，mergedCount=3；
 *  2) 回执：ack 后 read_by/read_at 回填；重复 ack 幂等（保留首次回执人）；
 *  3) 简报：近 7 天按事件类型聚合（总数/未回执数），回执后未回执数下降；
 *  4) 隔离：org13 视角看不到 org12 的提醒，也 ack 不动它。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "grc.scheduler.enabled=false")
@Testcontainers
class NotifyReceiptTest {

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
    private WorkbenchService service;

    private static final long ORG_PAY = 12L;
    private static final long ORG_CF = 13L;

    @BeforeEach
    void seed() throws Exception {
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement()) {
            s.executeUpdate("TRUNCATE reminder_dispatch_log RESTART IDENTITY CASCADE");
            // org12：同一审计计划 3 个阈值日的提醒（应被降噪合并）+ 1 条报送提醒
            s.executeUpdate("INSERT INTO reminder_dispatch_log(object_type, object_id, event_type, threshold_key, org_id) VALUES "
                    + "('AUDIT_PLAN', 1, 'EXT_AUDIT_PLAN_APPROACHING', '15', 12),"
                    + "('AUDIT_PLAN', 1, 'EXT_AUDIT_PLAN_APPROACHING', '10', 12),"
                    + "('AUDIT_PLAN', 1, 'EXT_AUDIT_PLAN_APPROACHING', '5', 12),"
                    + "('REG_FILING', 2, 'REG_FILING_DUE', '10', 12)");
        }
    }

    @AfterEach
    void clearContext() {
        IsolationContext.clear();
    }

    @Test
    void 降噪合并_回执_简报() {
        List<NotificationView> list = asOrg(ORG_PAY, () -> service.notifications(null));
        assertEquals(2, list.size(), "4 条提醒应降噪合并为 2 条（同对象同事件合并）");
        NotificationView merged = list.stream()
                .filter(n -> "AUDIT_PLAN".equals(n.objectType())).findFirst().orElseThrow();
        assertEquals(3, merged.mergedCount(), "同一计划 3 个阈值日合并计数");
        assertNull(merged.readBy(), "未回执");

        // 回执 + 幂等
        asOrg(ORG_PAY, () -> { service.ackNotification(merged.id(), "u1"); return null; });
        asOrg(ORG_PAY, () -> { service.ackNotification(merged.id(), "u2"); return null; });
        NotificationView acked = asOrg(ORG_PAY, () -> service.notifications(null)).stream()
                .filter(n -> n.id() == merged.id()).findFirst().orElseThrow();
        assertEquals("u1", acked.readBy(), "重复回执保留首次回执人");

        // 简报：AUDIT 事件 3 条中 1 条已回执 → unread=2
        List<WorkbenchService.DigestRow> digest = asOrg(ORG_PAY, () -> service.digest(7));
        WorkbenchService.DigestRow d = digest.stream()
                .filter(x -> "EXT_AUDIT_PLAN_APPROACHING".equals(x.eventType())).findFirst().orElseThrow();
        assertEquals(3, d.total());
        assertEquals(2, d.unread(), "回执 1 条后未回执 2 条");
    }

    @Test
    void m10_11_批量回执_仅可见组织未回执生效且幂等() {
        // org12 一次性回执全部 4 条原始提醒（RESTART IDENTITY → id 1..4）
        int acked = asOrg(ORG_PAY, () -> service.ackNotifications(List.of(1L, 2L, 3L, 4L), "batch"));
        assertEquals(4, acked, "4 条未回执提醒应全部回执");
        // 简报：所有事件未回执归零
        assertTrue(asOrg(ORG_PAY, () -> service.digest(7)).stream().allMatch(d -> d.unread() == 0),
                "批量回执后无未回执");
        // 幂等：再批量回执返回 0（已回执不重复）
        assertEquals(0, asOrg(ORG_PAY, () -> service.ackNotifications(List.of(1L, 2L, 3L, 4L), "batch2")));
    }

    @Test
    void m10_11_批量回执跨组织无效() {
        assertEquals(0, asOrg(ORG_CF, () -> service.ackNotifications(List.of(1L, 2L, 3L, 4L), "spy")),
                "org13 批量回执 org12 的提醒应 0 生效（RLS 兜底）");
    }

    @Test
    void 隔离_org13不可见不可回执() {
        assertTrue(asOrg(ORG_CF, () -> service.notifications(null)).isEmpty(), "org13 不应看到 org12 的提醒");
        assertTrue(asOrg(ORG_CF, () -> service.digest(7)).isEmpty());
        // ack org12 的提醒无效果
        long id = asOrg(ORG_PAY, () -> service.notifications(null)).get(0).id();
        asOrg(ORG_CF, () -> { service.ackNotification(id, "spy"); return null; });
        NotificationView still = asOrg(ORG_PAY, () -> service.notifications(null)).stream()
                .filter(n -> n.id() == id).findFirst().orElseThrow();
        assertNull(still.readBy(), "跨组织 ack 应无效");
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
