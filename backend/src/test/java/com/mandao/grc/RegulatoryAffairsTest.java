package com.mandao.grc;

import com.mandao.grc.common.isolation.IsolationContext;
import com.mandao.grc.kernel.ExpiryScanService;
import com.mandao.grc.modules.audit.ChainVerifyResult;
import com.mandao.grc.modules.audit.HashChainService;
import com.mandao.grc.modules.regulatory.MajorIncidentReport;
import com.mandao.grc.modules.regulatory.MajorIncidentService;
import com.mandao.grc.modules.regulatory.MajorIncidentStatus;
import com.mandao.grc.modules.regulatory.RegFiling;
import com.mandao.grc.modules.regulatory.RegFilingService;
import com.mandao.grc.modules.regulatory.RegFilingStatus;
import com.mandao.grc.modules.regulatory.RegInquiry;
import com.mandao.grc.modules.regulatory.RegInquiryService;
import com.mandao.grc.modules.regulatory.RegInquiryStatus;
import com.mandao.grc.modules.regulatory.RegPenalty;
import com.mandao.grc.modules.regulatory.RegPenaltyService;
import com.mandao.grc.modules.regulatory.RegPenaltyStatus;
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

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M11 监管事项集成测试（真实 PG + 应用切面 + RLS）。验证：
 *  1) 报送日历生命周期 PLANNED→PREPARING→SUBMITTED→ACCEPTED，非法流转被拒；
 *  2) 【法定时限预警 红线】reg_filing statutory_deadline=今+10、reminder_days={15,10}（库级默认），
 *     ExpiryScanService.scanOnce(今) 产 1 条 REG_FILING_DUE，重复扫描幂等不再产；
 *  3) 监管问询/处罚约谈/重大事件 各一个生命周期用例；
 *  4) 组织隔离：org12 的报送，在 org13 上下文中看不到；
 *  5) 留痕：流转后对应 org 哈希链 verify 通过且条数正确。
 *
 * scheduler.enabled=false 关闭定时器，由测试直接调用 scanOnce 保证确定性。
 * 设计依据：需求文档 M11、D1-2 §23、D1-1 §5.12、D2-5。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "grc.scheduler.enabled=false")
@Testcontainers
class RegulatoryAffairsTest {

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
    private RegFilingService filingService;

    @Autowired
    private RegInquiryService inquiryService;

    @Autowired
    private RegPenaltyService penaltyService;

    @Autowired
    private MajorIncidentService incidentService;

    @Autowired
    private HashChainService hashChainService;

    @Autowired
    private ExpiryScanService scanService;

    private static final long ORG_PAY = 12L;   // 支付子公司
    private static final long ORG_CF = 13L;     // 消费金融

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 24);

    /** 每用例前清空 M11 相关表与操作日志、调度表（owner 连接，绕 RLS；grc_app 无删权）。 */
    @BeforeEach
    void clean() throws Exception {
        execAsOwner("TRUNCATE reg_filing, reg_inquiry, reg_penalty, major_incident_report, "
                + "operation_log, reminder_dispatch_log, domain_event RESTART IDENTITY CASCADE");
    }

    @AfterEach
    void clearContext() {
        IsolationContext.clear();
    }

    // ---------- 报送日历生命周期 ----------

    @Test
    void 报送生命周期_计划到受理全程通过() {
        Long id = asOrg(ORG_PAY, () ->
                filingService.create(ORG_PAY, "反洗钱报表", "央行", TODAY.plusDays(30), "creator").getId());

        assertEquals(RegFilingStatus.PREPARING,
                asOrg(ORG_PAY, () -> filingService.prepare(id, "officer").getStatus()));
        assertEquals(RegFilingStatus.SUBMITTED,
                asOrg(ORG_PAY, () -> filingService.submit(id, "officer").getStatus()));
        assertEquals(RegFilingStatus.ACCEPTED,
                asOrg(ORG_PAY, () -> filingService.accept(id, "officer").getStatus()));
    }

    @Test
    void 报送非法流转_计划态直接受理被拒() {
        Long id = asOrg(ORG_PAY, () ->
                filingService.create(ORG_PAY, "X", "央行", TODAY.plusDays(30), "creator").getId());
        // PLANNED 不可直接 accept（accept 仅允许从 SUBMITTED）
        assertThrows(IllegalStateException.class,
                () -> runAsOrg(ORG_PAY, () -> filingService.accept(id, "officer")));
    }

    // ---------- 法定时限预警（红线，核心） ----------

    @Test
    void 法定时限预警_到提醒日产出报送到期事件() throws Exception {
        // statutory_deadline = today+10，reminder_days 由 V8 库级默认 {15,10} 填充 → 今天恰好命中 10 天提醒
        asOrg(ORG_PAY, () ->
                filingService.create(ORG_PAY, "反洗钱报表", "央行", TODAY.plusDays(10), "creator"));

        // 内核为系统级 actor（不在 modules 包，不受用户切面约束），直接 scanOnce。
        int emitted = scanService.scanOnce(TODAY).emitted();
        assertEquals(1, emitted, "应产出 1 条法定时限预警事件");
        assertEquals(1, countEvents("REG_FILING_DUE"), "domain_event 应有 1 条 REG_FILING_DUE");
    }

    @Test
    void 法定时限预警_幂等重复扫描不重复产() throws Exception {
        asOrg(ORG_PAY, () ->
                filingService.create(ORG_PAY, "反洗钱报表", "央行", TODAY.plusDays(10), "creator"));

        assertEquals(1, scanService.scanOnce(TODAY).emitted());
        assertEquals(0, scanService.scanOnce(TODAY).emitted(), "二次扫描应幂等不再产");
        assertEquals(1, countEvents("REG_FILING_DUE"), "事件总数仍为 1");
    }

    @Test
    void 法定时限预警_未到提醒日不产() {
        // 距法定时限还有 9 天，不在 {15,10} 中 → 不产
        asOrg(ORG_PAY, () ->
                filingService.create(ORG_PAY, "反洗钱报表", "央行", TODAY.plusDays(9), "creator"));
        assertEquals(0, scanService.scanOnce(TODAY).emitted());
    }

    // ---------- 监管问询生命周期 ----------

    @Test
    void 监管问询_收到答复了结全程通过() {
        Long id = asOrg(ORG_PAY, () ->
                inquiryService.create(ORG_PAY, "数据报送问询", "银保监", TODAY, TODAY.plusDays(15), "officer").getId());

        assertEquals(RegInquiryStatus.RESPONDING,
                asOrg(ORG_PAY, () -> inquiryService.respond(id, "officer").getStatus()));
        assertEquals(RegInquiryStatus.CLOSED,
                asOrg(ORG_PAY, () -> inquiryService.close(id, "officer").getStatus()));
    }

    // ---------- 处罚约谈生命周期 ----------

    @Test
    void 处罚约谈_登记整改了结全程通过() {
        Long id = asOrg(ORG_PAY, () ->
                penaltyService.create(ORG_PAY, "违规处罚", "央行", "罚款",
                        new BigDecimal("500000"), TODAY, "officer").getId());

        assertEquals(RegPenaltyStatus.RECTIFYING,
                asOrg(ORG_PAY, () -> penaltyService.rectify(id, "officer").getStatus()));
        assertEquals(RegPenaltyStatus.CLOSED,
                asOrg(ORG_PAY, () -> penaltyService.close(id, "officer").getStatus()));
    }

    // ---------- 重大事件报送生命周期 ----------

    @Test
    void 重大事件_草稿上报了结全程通过() {
        Long id = asOrg(ORG_PAY, () ->
                incidentService.create(ORG_PAY, "系统中断", "HIGH", OffsetDateTime.now(), "officer").getId());

        MajorIncidentReport reported = asOrg(ORG_PAY, () -> incidentService.report(id, "officer"));
        assertEquals(MajorIncidentStatus.REPORTED, reported.getStatus());
        assertTrue(reported.getReportedAt() != null, "上报后应记录 reported_at");
        assertEquals(MajorIncidentStatus.CLOSED,
                asOrg(ORG_PAY, () -> incidentService.close(id, "officer").getStatus()));
    }

    // ---------- 组织隔离 ----------

    @Test
    void 组织隔离_org12的报送org13看不到() {
        asOrg(ORG_PAY, () ->
                filingService.create(ORG_PAY, "仅支付可见", "央行", TODAY.plusDays(30), "officer"));

        List<RegFiling> payView = asOrg(ORG_PAY, () -> filingService.list());
        assertEquals(1, payView.size(), "org12 应看到自己的 1 条报送");

        List<RegFiling> cfView = asOrg(ORG_CF, () -> filingService.list());
        assertTrue(cfView.isEmpty(), "org13 不应看到 org12 的报送");
    }

    // ---------- 留痕 ----------

    @Test
    void 留痕_报送流转后哈希链校验通过且有记录() {
        Long id = asOrg(ORG_PAY, () ->
                filingService.create(ORG_PAY, "留痕报送", "央行", TODAY.plusDays(30), "c").getId()); // CREATE=1
        asOrg(ORG_PAY, () -> filingService.prepare(id, "o"));   // PREPARE=2
        asOrg(ORG_PAY, () -> filingService.submit(id, "o"));    // SUBMIT=3
        asOrg(ORG_PAY, () -> filingService.accept(id, "o"));    // ACCEPT=4

        ChainVerifyResult r = asOrg(ORG_PAY, () -> hashChainService.verify(ORG_PAY));
        assertTrue(r.valid(), "留痕后链应校验通过");
        assertEquals(4, r.count(), "应有 4 条 M11 报送操作留痕");
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

    private void execAsOwner(String sql) throws Exception {
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement()) {
            s.executeUpdate(sql);
        }
    }

    private long countEvents(String eventType) throws Exception {
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement();
             ResultSet rs = s.executeQuery(
                     "SELECT count(*) FROM domain_event WHERE event_type = '" + eventType + "'")) {
            rs.next();
            return rs.getLong(1);
        }
    }
}
