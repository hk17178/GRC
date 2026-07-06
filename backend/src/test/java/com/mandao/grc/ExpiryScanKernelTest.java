package com.mandao.grc;

import com.mandao.grc.kernel.ExpiryScanService;
import com.mandao.grc.kernel.ScanResult;
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
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 调度/到期扫描内核集成测试（真实 PG）。验证：
 *  1) 到提醒日产出 EXT_AUDIT_PLAN_APPROACHING 事件；
 *  2) 幂等——重复扫描不重复产；
 *  3) 阈值精确——只在命中 reminder_days 的那天产；
 *  4) 多 org 各自产、互不影响。
 *
 * scheduler.enabled=false 关闭定时器，由测试直接调用 scanOnce 保证确定性。
 * 设计依据：D1-1 §5.12、D1-9 H-01、TC-M3-104。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "grc.scheduler.enabled=false")
@Testcontainers
class ExpiryScanKernelTest {

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
    private ExpiryScanService scanService;

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 24);

    /** 每用例前清空内核相关表（owner 连接，绕 RLS）。 */
    @BeforeEach
    void clean() throws Exception {
        // CASCADE：M3(V6) 起 audit_finding 外键引用 audit_plan，截断 audit_plan 须级联（否则被 FK 阻止）
        execAsOwner("TRUNCATE audit_plan, reminder_dispatch_log, domain_event RESTART IDENTITY CASCADE");
        execAsOwner("DELETE FROM certificate");                 // B24
        execAsOwner("DELETE FROM policy WHERE org_id IN (12,13)"); // B23（迁移不含 policy 种子，本测试自插自清）
    }

    /** 以 owner 直连插入一条外审计划（绕 RLS，模拟数据已存在）。 */
    private void seedPlan(long orgId, String title, LocalDate startDate, String reminderDaysArray) throws Exception {
        execAsOwner("INSERT INTO audit_plan(org_id, title, plan_start_date, reminder_days) VALUES ("
                + orgId + ", '" + title + "', DATE '" + startDate + "', '" + reminderDaysArray + "')");
    }

    @Test
    void 到提醒日产出临近事件() throws Exception {
        // 计划开始日 = today+10，reminder_days={15,10} → 今天恰好命中 10 天提醒
        seedPlan(12L, "支付-年度外审", TODAY.plusDays(10), "{15,10}");

        ScanResult r = scanService.scanOnce(TODAY);

        assertEquals(1, r.emitted(), "应产出 1 条临近事件");
        assertEquals(1, countEvents("EXT_AUDIT_PLAN_APPROACHING"), "domain_event 应有 1 条");
    }

    @Test
    void 幂等_重复扫描不重复产() throws Exception {
        seedPlan(12L, "支付-年度外审", TODAY.plusDays(10), "{15,10}");

        assertEquals(1, scanService.scanOnce(TODAY).emitted());
        assertEquals(0, scanService.scanOnce(TODAY).emitted(), "二次扫描应幂等不再产");
        assertEquals(1, countEvents("EXT_AUDIT_PLAN_APPROACHING"), "事件总数仍为 1");
    }

    @Test
    void b24_证书到期恰余30天产CERT_EXPIRY事件() throws Exception {
        // 到期日 = today+30，命中 {60,30,7} 的 30 天档
        execAsOwner("INSERT INTO certificate(org_id, name, expiry_date, status) VALUES "
                + "(12, 'ISO27001 认证', DATE '" + TODAY.plusDays(30) + "', 'VALID')");
        assertEquals(1, scanService.scanOnce(TODAY).emitted(), "应产 1 条证书到期事件");
        assertEquals(1, countEvents("CERT_EXPIRY"), "domain_event 应有 1 条 CERT_EXPIRY");
        // 幂等
        assertEquals(0, scanService.scanOnce(TODAY).emitted());
    }

    @Test
    void b24_已吊销证书不产事件() throws Exception {
        execAsOwner("INSERT INTO certificate(org_id, name, expiry_date, status) VALUES "
                + "(12, '已吊销证书', DATE '" + TODAY.plusDays(30) + "', 'REVOKED')");
        assertEquals(0, scanService.scanOnce(TODAY).emitted(), "非 VALID 证书不产到期事件");
    }

    @Test
    void b23_制度复审到期恰余7天产POLICY_REVIEW_DUE事件() throws Exception {
        // 复审日 = 生效日 + 复审周期月。取 生效日 = today+7 - 12 月、周期 12 月 → 复审日 = today+7，命中 {30,7,0} 的 7 天档
        java.time.LocalDate effective = TODAY.plusDays(7).minusMonths(12);
        execAsOwner("INSERT INTO policy(org_id, code, title, status, version, effective_date, review_cycle_months) VALUES "
                + "(12, 'POL-REV-1', '数据安全管理制度', 'EFFECTIVE', 1, DATE '" + effective + "', 12)");
        assertEquals(1, scanService.scanOnce(TODAY).emitted(), "应产 1 条制度复审到期事件");
        assertEquals(1, countEvents("POLICY_REVIEW_DUE"), "domain_event 应有 1 条 POLICY_REVIEW_DUE");
    }

    @Test
    void 阈值精确_未到提醒日不产() throws Exception {
        // 距开始还有 9 天，不在 {15,10} 中 → 不产
        seedPlan(12L, "支付-年度外审", TODAY.plusDays(9), "{15,10}");
        assertEquals(0, scanService.scanOnce(TODAY).emitted());
    }

    @Test
    void 多org各自产互不影响() throws Exception {
        seedPlan(12L, "支付-外审", TODAY.plusDays(15), "{15,10}");
        seedPlan(13L, "消金-外审", TODAY.plusDays(15), "{15,10}");

        assertEquals(2, scanService.scanOnce(TODAY).emitted(), "两个 org 各产 1 条");
        assertEquals(1, countEventsForOrg("EXT_AUDIT_PLAN_APPROACHING", 12L));
        assertEquals(1, countEventsForOrg("EXT_AUDIT_PLAN_APPROACHING", 13L));
    }

    // ---------- 测试辅助 ----------

    private void execAsOwner(String sql) throws Exception {
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement()) {
            s.executeUpdate(sql);
        }
    }

    private long countEvents(String eventType) throws Exception {
        return queryCount("SELECT count(*) FROM domain_event WHERE event_type = '" + eventType + "'");
    }

    private long countEventsForOrg(String eventType, long orgId) throws Exception {
        return queryCount("SELECT count(*) FROM domain_event WHERE event_type = '" + eventType
                + "' AND org_id = " + orgId);
    }

    private long queryCount(String sql) throws Exception {
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement();
             ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }
}
