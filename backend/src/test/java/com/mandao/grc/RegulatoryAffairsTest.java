package com.mandao.grc;

import com.mandao.grc.common.isolation.IsolationContext;
import com.mandao.grc.kernel.ExpiryScanService;
import com.mandao.grc.modules.audit.ChainVerifyResult;
import com.mandao.grc.modules.audit.HashChainService;
import com.mandao.grc.modules.regulatory.MajorIncidentReport;
import com.mandao.grc.modules.regulatory.MajorIncidentService;
import com.mandao.grc.modules.regulatory.MajorIncidentSeverity;
import com.mandao.grc.modules.regulatory.MajorIncidentStatus;
import com.mandao.grc.modules.regulatory.RegFiling;
import com.mandao.grc.modules.regulatory.RegFilingService;
import com.mandao.grc.modules.regulatory.RegFilingStatus;
import com.mandao.grc.modules.workflow.ApprovalDecision;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
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
 *  1) 报送日历生命周期 TO_DRAFT→DRAFTING→SUBMITTED→CLOSED，非法流转被拒；
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

    @Autowired
    private com.mandao.grc.modules.regulatory.RegFilingScheduleService scheduleService;

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private HistoryService historyService;

    private static final long ORG_PAY = 12L;   // 支付子公司
    private static final long ORG_CF = 13L;     // 消费金融

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 24);

    /** 每用例前清空 M11 相关表与操作日志、调度表（owner 连接，绕 RLS；grc_app 无删权）。 */
    @BeforeEach
    void clean() throws Exception {
        execAsOwner("TRUNCATE reg_filing, reg_inquiry, reg_penalty, major_incident_report, reg_filing_schedule, "
                + "operation_log, reminder_dispatch_log, domain_event RESTART IDENTITY CASCADE");
        // 七轮 7-2：回执证据挂 filing_id/incident_id——RESTART IDENTITY 会让 id 跨用例复用，
        // 残留回执会误放行了结门控，必须一并清掉
        execAsOwner("DELETE FROM evidence WHERE filing_id IS NOT NULL OR incident_id IS NOT NULL "
                + "OR inquiry_id IS NOT NULL OR penalty_id IS NOT NULL");
        // 清理 Flowable 运行态/历史：reg_filing id 因 RESTART IDENTITY 跨用例复用，会使
        // 报送复核 businessKey(REG_FILING:{id}) 跨用例碰撞，须净化（生产 id 全局唯一无此问题）。
        runtimeService.createProcessInstanceQuery().list()
                .forEach(pi -> runtimeService.deleteProcessInstance(pi.getId(), "用例重置"));
        historyService.createHistoricProcessInstanceQuery().list()
                .forEach(hpi -> historyService.deleteHistoricProcessInstance(hpi.getId()));
    }

    @AfterEach
    void clearContext() {
        IsolationContext.clear();
    }

    // ---------- 报送日历生命周期 ----------

    @Test
    void 报送生命周期_待起草到了结全程通过() throws Exception {
        Long id = asOrg(ORG_PAY, () ->
                filingService.create(ORG_PAY, "反洗钱报表", "央行", TODAY.plusDays(30), "creator").getId());

        assertEquals(RegFilingStatus.DRAFTING,
                asOrg(ORG_PAY, () -> filingService.prepare(id, "officer").getStatus()));
        // 提交复核（启动审批）→ 复核中
        assertEquals(RegFilingStatus.PENDING_REVIEW,
                asOrg(ORG_PAY, () -> filingService.submitForReview(id, "officer").getStatus()));
        // 复核通过 → 正式报送
        assertEquals(RegFilingStatus.SUBMITTED,
                asOrg(ORG_PAY, () -> filingService.decideSubmit(id, ApprovalDecision.APPROVED, "lead", "材料合规").getStatus()));
        // 七轮 7-2（B2 红线）：无回执证据不可了结 → 挂回执后放行
        assertThrows(IllegalStateException.class,
                () -> runAsOrg(ORG_PAY, () -> filingService.close(id, "officer")));
        seedReceiptEvidence(id, null);
        assertEquals(RegFilingStatus.CLOSED,
                asOrg(ORG_PAY, () -> filingService.close(id, "officer").getStatus()));
    }

    /** 七轮 7-2：owner 直插一条回执证据（filing/incident 二选一挂接）。 */
    private void seedReceiptEvidence(Long filingId, Long incidentId) throws Exception {
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement()) {
            s.executeUpdate("INSERT INTO evidence(org_id, filing_id, incident_id, name, data, sha256, uploaded_by) "
                    + "VALUES (12, " + (filingId == null ? "NULL" : filingId) + ", "
                    + (incidentId == null ? "NULL" : incidentId) + ", '监管回执', '\\x01', 'seed-sha', 'officer')");
        }
    }

    /** M11 B13：为问询/处罚挂举证证据（owner 直插，不走留痕链，仅满足计数门控）。 */
    private void seedRegEvidence(String col, Long refId) throws Exception {
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement()) {
            s.executeUpdate("INSERT INTO evidence(org_id, " + col + ", name, data, sha256, uploaded_by) "
                    + "VALUES (12, " + refId + ", '举证材料', '\\x01', 'seed-sha', 'officer')");
        }
    }

    @Test
    void 报送复核驳回_退回起草后可再次提交复核() {
        Long id = asOrg(ORG_PAY, () ->
                filingService.create(ORG_PAY, "支付统计报表", "央行", TODAY.plusDays(20), "creator").getId());
        asOrg(ORG_PAY, () -> filingService.prepare(id, "officer"));
        asOrg(ORG_PAY, () -> filingService.submitForReview(id, "officer"));
        // 复核驳回 → 退回起草
        assertEquals(RegFilingStatus.DRAFTING,
                asOrg(ORG_PAY, () -> filingService.decideSubmit(id, ApprovalDecision.REJECTED, "lead", "数据口径需修订").getStatus()));
        // 退回后可再次提交复核
        assertEquals(RegFilingStatus.PENDING_REVIEW,
                asOrg(ORG_PAY, () -> filingService.submitForReview(id, "officer").getStatus()));
    }

    @Test
    void 报送非法流转_待起草态直接了结被拒() {
        Long id = asOrg(ORG_PAY, () ->
                filingService.create(ORG_PAY, "X", "央行", TODAY.plusDays(30), "creator").getId());
        // TO_DRAFT 不可直接 close（close 仅允许从 SUBMITTED）
        assertThrows(IllegalStateException.class,
                () -> runAsOrg(ORG_PAY, () -> filingService.close(id, "officer")));
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

    // ---------- 周期性报送计划（B34） ----------

    @Test
    void b34_周期计划到期_自动生成报送实例并推进下次到期() {
        // 季报，首次到期 today+10，lead=15 → 已进入生成窗口
        Long sid = asOrg(ORG_PAY, () -> scheduleService.create(ORG_PAY, "反洗钱季度报表", "人民银行",
                com.mandao.grc.modules.regulatory.RegFilingSchedule.Period.QUARTERLY, 15, TODAY.plusDays(10), "officer").getId());

        int emitted = scanService.scanOnce(TODAY).emitted();
        assertTrue(emitted >= 1, "应至少产 1 条周期报送生成事件");
        // 生成了一份 reg_filing 草稿
        assertEquals(1, asOrg(ORG_PAY, () -> filingService.list()).size(), "应自动生成 1 份报送实例");
        // 幂等：同一到期日再扫不重复生成
        scanService.scanOnce(TODAY);
        assertEquals(1, asOrg(ORG_PAY, () -> filingService.list()).size(), "同一到期日不应重复生成");
        // next_due 已推进到下个季度（+3 月）
        var sched = asOrg(ORG_PAY, () -> scheduleService.get(sid));
        assertEquals(TODAY.plusDays(10).plusMonths(3), sched.getNextDue(), "季报应推进到 +3 个月");
    }

    @Test
    void b34_停用计划_不再生成() {
        Long sid = asOrg(ORG_PAY, () -> scheduleService.create(ORG_PAY, "停用测试", "人民银行",
                com.mandao.grc.modules.regulatory.RegFilingSchedule.Period.MONTHLY, 15, TODAY.plusDays(5), "officer").getId());
        asOrg(ORG_PAY, () -> scheduleService.setEnabled(sid, false, "officer"));
        scanService.scanOnce(TODAY);
        assertTrue(asOrg(ORG_PAY, () -> filingService.list()).isEmpty(), "停用后不应生成实例");
    }

    // ---------- 监管问询生命周期 ----------

    @Test
    void 监管问询_起草答复待反馈了结全程通过() throws Exception {
        Long id = asOrg(ORG_PAY, () ->
                inquiryService.create(ORG_PAY, "数据报送问询", "银保监", TODAY, TODAY.plusDays(15), "officer").getId());

        // M11 B13：无答复材料证据不可答复 → 挂证据后放行
        assertThrows(IllegalStateException.class,
                () -> runAsOrg(ORG_PAY, () -> inquiryService.reply(id, "officer")));
        seedRegEvidence("inquiry_id", id);
        assertEquals(RegInquiryStatus.REPLIED,
                asOrg(ORG_PAY, () -> inquiryService.reply(id, "officer").getStatus()));
        assertEquals(RegInquiryStatus.AWAIT_FEEDBACK,
                asOrg(ORG_PAY, () -> inquiryService.awaitFeedback(id, "officer").getStatus()));
        assertEquals(RegInquiryStatus.CLOSED,
                asOrg(ORG_PAY, () -> inquiryService.close(id, "officer").getStatus()));
    }

    // ---------- 处罚约谈生命周期 ----------

    @Test
    void 处罚约谈_登记整改了结全程通过() throws Exception {
        Long id = asOrg(ORG_PAY, () ->
                penaltyService.create(ORG_PAY, "违规处罚", "央行", "罚款",
                        new BigDecimal("500000"), TODAY, "officer").getId());

        assertEquals(RegPenaltyStatus.RECTIFYING,
                asOrg(ORG_PAY, () -> penaltyService.rectify(id, "officer").getStatus()));
        // M11 B13：无整改/缴款凭证不可了结 → 挂证据后放行
        assertThrows(IllegalStateException.class,
                () -> runAsOrg(ORG_PAY, () -> penaltyService.close(id, "officer")));
        seedRegEvidence("penalty_id", id);
        assertEquals(RegPenaltyStatus.CLOSED,
                asOrg(ORG_PAY, () -> penaltyService.close(id, "officer").getStatus()));
    }

    // ---------- 重大事件报送生命周期 ----------

    @Test
    void 重大事件_草稿上报确认挂回执了结全程通过() throws Exception {
        // 七轮 7-2（B3）：状态机扩 ACKNOWLEDGED 段 + 了结须回执证据
        Long id = asOrg(ORG_PAY, () ->
                incidentService.create(ORG_PAY, "系统中断", MajorIncidentSeverity.HIGH, OffsetDateTime.now(),
                        null, "officer").getId());

        MajorIncidentReport reported = asOrg(ORG_PAY, () -> incidentService.report(id, "officer"));
        assertEquals(MajorIncidentStatus.REPORTED, reported.getStatus());
        assertEquals(MajorIncidentSeverity.HIGH, reported.getSeverity(), "严重度应为平台五级 HIGH");
        assertTrue(reported.getReportedAt() != null, "上报后应记录 reported_at");
        assertEquals(MajorIncidentStatus.ACKNOWLEDGED,
                asOrg(ORG_PAY, () -> incidentService.acknowledge(id, "officer").getStatus()));
        // 无回执不可了结 → 挂回执后放行
        assertThrows(IllegalStateException.class,
                () -> runAsOrg(ORG_PAY, () -> incidentService.close(id, "officer")));
        seedReceiptEvidence(null, id);
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
    void 留痕_报送流转后哈希链校验通过且有记录() throws Exception {
        Long id = asOrg(ORG_PAY, () ->
                filingService.create(ORG_PAY, "留痕报送", "央行", TODAY.plusDays(30), "c").getId()); // CREATE=1
        asOrg(ORG_PAY, () -> filingService.prepare(id, "o"));   // PREPARE=2
        // 提交复核(SUBMIT_REVIEW + WORKFLOW_SUBMIT)=3,4；复核通过(WORKFLOW_DECIDE + SUBMIT)=5,6
        asOrg(ORG_PAY, () -> filingService.submitForReview(id, "o"));
        asOrg(ORG_PAY, () -> filingService.decideSubmit(id, ApprovalDecision.APPROVED, "lead", "ok"));
        seedReceiptEvidence(id, null); // 七轮 7-2：了结前置回执（owner 直插不走留痕链，不影响计数）
        asOrg(ORG_PAY, () -> filingService.close(id, "o"));     // CLOSE=7

        ChainVerifyResult r = asOrg(ORG_PAY, () -> hashChainService.verify(ORG_PAY));
        assertTrue(r.valid(), "留痕后链应校验通过");
        assertEquals(7, r.count(), "应有 7 条 M11 报送操作留痕（含复核审批两段+工作流两条）");
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
