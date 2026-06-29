package com.mandao.grc;

import com.mandao.grc.common.isolation.IsolationContext;
import com.mandao.grc.kernel.ExpiryScanService;
import com.mandao.grc.modules.audit.ChainVerifyResult;
import com.mandao.grc.modules.audit.HashChainService;
import com.mandao.grc.modules.audit.management.AuditFinding;
import com.mandao.grc.modules.audit.management.AuditFindingService;
import com.mandao.grc.modules.audit.management.AuditFindingStatus;
import com.mandao.grc.modules.audit.management.AuditFunnelException;
import com.mandao.grc.modules.audit.management.AuditPlan;
import com.mandao.grc.modules.audit.management.AuditPlanService;
import com.mandao.grc.modules.audit.management.AuditPlanStatus;
import com.mandao.grc.modules.audit.management.AuditSeverity;
import com.mandao.grc.modules.audit.management.AuditType;
import com.mandao.grc.modules.audit.management.ExternalResponseStatus;
import com.mandao.grc.modules.audit.management.RemediationService;
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
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M3 审计管理集成测试（真实 PG + 应用切面 + RLS）。验证：
 *  1) 审计计划生命周期 PLANNED→IN_PROGRESS→REPORTING→CLOSED 通过，非法流转被拒，取消(CANCELLED)可达；
 *  2) 【外审对外回函三段漏斗 红线】SUBMITTED→ACCEPTED→CLOSED 正序通过；
 *     跳级被拒、逆向被拒、非外审走漏斗被拒；唯 CLOSED 算闭环；
 *  3) 组织隔离：org12 的审计发现，在 org13 上下文中看不到；
 *  4) 留痕：流转/漏斗推进后对应 org 哈希链 verify 通过且有记录；
 *  5) 【调度兼容】扩展 audit_plan 后，ExpiryScanService.scanOnce 仍能对 EXTERNAL 计划产 EXT_AUDIT_PLAN_APPROACHING。
 *
 * 设计依据：需求文档 M3 审计管理（外审三段漏斗）、D1-3 §5.1/§8、D2-5。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "grc.scheduler.enabled=false")
@Testcontainers
class AuditManagementTest {

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
    private AuditPlanService planService;

    @Autowired
    private AuditFindingService findingService;

    @Autowired
    private RemediationService remediationService;

    @Autowired
    private HashChainService hashChainService;

    @Autowired
    private ExpiryScanService scanService;

    private static final long ORG_PAY = 12L;   // 支付子公司
    private static final long ORG_CF = 13L;     // 消费金融

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 24);

    /**
     * 每用例前清空 M3 相关表与操作日志、调度表（owner 连接，绕 RLS；grc_app 无删权）。
     * audit_finding 外键引用 audit_plan，CASCADE 一并清；reminder_dispatch_log/domain_event 为调度兼容用例所需。
     */
    @BeforeEach
    void clean() throws Exception {
        execAsOwner("TRUNCATE audit_finding, audit_plan, operation_log, reminder_dispatch_log, domain_event "
                + "RESTART IDENTITY CASCADE");
    }

    @AfterEach
    void clearContext() {
        IsolationContext.clear();
    }

    // ---------- 审计计划生命周期 ----------

    @Test
    void 计划生命周期_计划到关闭全程通过() {
        Long id = asOrg(ORG_PAY, () ->
                planService.create(ORG_PAY, "2026 支付年度外审", AuditType.EXTERNAL, TODAY.plusDays(30), "creator").getId());

        assertEquals(AuditPlanStatus.IN_PROGRESS,
                asOrg(ORG_PAY, () -> planService.start(id, "auditor").getStatus()));
        assertEquals(AuditPlanStatus.REPORTING,
                asOrg(ORG_PAY, () -> planService.report(id, "auditor").getStatus()));
        assertEquals(AuditPlanStatus.CLOSED,
                asOrg(ORG_PAY, () -> planService.close(id, "lead").getStatus()));
    }

    @Test
    void 按类型过滤_内审外审分视图() {
        asOrg(ORG_PAY, () -> planService.create(ORG_PAY, "内审-支付系统", AuditType.INTERNAL, TODAY.plusDays(10), "c"));
        asOrg(ORG_PAY, () -> planService.create(ORG_PAY, "外审-年度", AuditType.EXTERNAL, TODAY.plusDays(20), "c"));

        var internal = asOrg(ORG_PAY, () -> planService.listByType(AuditType.INTERNAL));
        assertEquals(1, internal.size(), "INTERNAL 过滤应只返回内审");
        assertEquals(AuditType.INTERNAL, internal.get(0).getAuditType());
        assertEquals(1, asOrg(ORG_PAY, () -> planService.listByType(AuditType.EXTERNAL)).size(), "EXTERNAL 过滤应只返回外审");
        assertEquals(2, asOrg(ORG_PAY, () -> planService.listByType(null)).size(), "null 返回全部");
    }

    @Test
    void 计划非法流转_计划态直接关闭被拒() {
        Long id = asOrg(ORG_PAY, () ->
                planService.create(ORG_PAY, "X", AuditType.INTERNAL, TODAY.plusDays(30), "creator").getId());
        // PLANNED 不可直接 close（close 仅允许从 REPORTING）
        assertThrows(IllegalStateException.class,
                () -> runAsOrg(ORG_PAY, () -> planService.close(id, "lead")));
    }

    @Test
    void 计划取消_执行中可取消到终态() {
        Long id = asOrg(ORG_PAY, () ->
                planService.create(ORG_PAY, "拟取消外审", AuditType.EXTERNAL, TODAY.plusDays(30), "creator").getId());
        // PLANNED → IN_PROGRESS → CANCELLED（终态）
        asOrg(ORG_PAY, () -> planService.start(id, "auditor"));
        assertEquals(AuditPlanStatus.CANCELLED,
                asOrg(ORG_PAY, () -> planService.cancel(id, "lead").getStatus()));
        // 已 CANCELLED（终态）不可再关闭
        assertThrows(IllegalStateException.class,
                () -> runAsOrg(ORG_PAY, () -> planService.close(id, "lead")));
    }

    // ---------- 外审对外回函三段漏斗（红线，核心） ----------

    @Test
    void 外审漏斗_正序提交受理确认全程通过且唯确认算闭环() {
        Long pid = asOrg(ORG_PAY, () ->
                planService.create(ORG_PAY, "外审计划", AuditType.EXTERNAL, TODAY.plusDays(30), "c").getId());
        Long fid = asOrg(ORG_PAY, () ->
                findingService.createFinding(ORG_PAY, pid, "控制缺陷", AuditSeverity.HIGH, "auditor").getId());

        assertEquals(ExternalResponseStatus.SUBMITTED,
                asOrg(ORG_PAY, () -> findingService.submitResponse(fid, "auditor").getExternalResponseStatus()));
        assertEquals(ExternalResponseStatus.ACCEPTED,
                asOrg(ORG_PAY, () -> findingService.acceptResponse(fid, "auditor").getExternalResponseStatus()));

        AuditFinding closed = asOrg(ORG_PAY, () -> findingService.confirmClose(fid, "auditor"));
        assertEquals(ExternalResponseStatus.CLOSED, closed.getExternalResponseStatus());
        assertTrue(closed.getExternalResponseStatus().isClosed(), "唯 CLOSED 算外审闭环");
    }

    @Test
    void 外审漏斗_跳级被拒() {
        Long pid = asOrg(ORG_PAY, () ->
                planService.create(ORG_PAY, "外审计划", AuditType.EXTERNAL, TODAY.plusDays(30), "c").getId());
        Long fid = asOrg(ORG_PAY, () ->
                findingService.createFinding(ORG_PAY, pid, "f", AuditSeverity.MID, "a").getId());

        // 已提交 SUBMITTED 后，直接 confirmClose（跳过 ACCEPTED）→ 被拒
        asOrg(ORG_PAY, () -> findingService.submitResponse(fid, "a"));
        assertThrows(AuditFunnelException.class,
                () -> runAsOrg(ORG_PAY, () -> findingService.confirmClose(fid, "a")));
    }

    @Test
    void 外审漏斗_起点跳级被拒() {
        Long pid = asOrg(ORG_PAY, () ->
                planService.create(ORG_PAY, "外审计划", AuditType.EXTERNAL, TODAY.plusDays(30), "c").getId());
        Long fid = asOrg(ORG_PAY, () ->
                findingService.createFinding(ORG_PAY, pid, "f", AuditSeverity.MID, "a").getId());

        // 未进入漏斗(null) 直接 acceptResponse（跳过 SUBMITTED）→ 被拒
        assertThrows(AuditFunnelException.class,
                () -> runAsOrg(ORG_PAY, () -> findingService.acceptResponse(fid, "a")));
    }

    @Test
    void 外审漏斗_逆向被拒() {
        Long pid = asOrg(ORG_PAY, () ->
                planService.create(ORG_PAY, "外审计划", AuditType.EXTERNAL, TODAY.plusDays(30), "c").getId());
        Long fid = asOrg(ORG_PAY, () ->
                findingService.createFinding(ORG_PAY, pid, "f", AuditSeverity.MID, "a").getId());

        asOrg(ORG_PAY, () -> findingService.submitResponse(fid, "a"));
        asOrg(ORG_PAY, () -> findingService.acceptResponse(fid, "a"));
        // 已到 ACCEPTED，再 submitResponse（逆向回 SUBMITTED）→ 被拒
        assertThrows(AuditFunnelException.class,
                () -> runAsOrg(ORG_PAY, () -> findingService.submitResponse(fid, "a")));
    }

    @Test
    void 外审漏斗_原地重复被拒() {
        Long pid = asOrg(ORG_PAY, () ->
                planService.create(ORG_PAY, "外审计划", AuditType.EXTERNAL, TODAY.plusDays(30), "c").getId());
        Long fid = asOrg(ORG_PAY, () ->
                findingService.createFinding(ORG_PAY, pid, "f", AuditSeverity.MID, "a").getId());

        asOrg(ORG_PAY, () -> findingService.submitResponse(fid, "a"));
        // 已 SUBMITTED，再次 submitResponse（原地重复）→ 被拒
        assertThrows(AuditFunnelException.class,
                () -> runAsOrg(ORG_PAY, () -> findingService.submitResponse(fid, "a")));
    }

    @Test
    void 外审漏斗_非外审发现走漏斗被拒() {
        // 内审计划下的发现走对外回函漏斗 → 被拒
        Long pid = asOrg(ORG_PAY, () ->
                planService.create(ORG_PAY, "内审计划", AuditType.INTERNAL, TODAY.plusDays(30), "c").getId());
        Long fid = asOrg(ORG_PAY, () ->
                findingService.createFinding(ORG_PAY, pid, "f", AuditSeverity.MID, "a").getId());

        assertThrows(AuditFunnelException.class,
                () -> runAsOrg(ORG_PAY, () -> findingService.submitResponse(fid, "a")));
    }

    // ---------- 内部处置状态机 ----------

    @Test
    void 内部处置_分析整改关闭全程通过() {
        Long pid = asOrg(ORG_PAY, () ->
                planService.create(ORG_PAY, "内审计划", AuditType.INTERNAL, TODAY.plusDays(30), "c").getId());
        Long fid = asOrg(ORG_PAY, () ->
                findingService.createFinding(ORG_PAY, pid, "f", AuditSeverity.LOW, "a").getId());

        assertEquals(AuditFindingStatus.ANALYZING,
                asOrg(ORG_PAY, () -> findingService.analyze(fid, "a").getStatus()));

        // 验证闭环：须有一条已验证的整改工单方可标记已整改（派单→开始→提交→验证）
        Long oid = asOrg(ORG_PAY, () ->
                remediationService.create(fid, "owner", TODAY.plusDays(7), "整改措施", "lead").getId());
        asOrg(ORG_PAY, () -> remediationService.start(oid, "owner"));
        asOrg(ORG_PAY, () -> remediationService.submit(oid, "已整改", "owner"));
        asOrg(ORG_PAY, () -> remediationService.verify(oid, "a"));

        assertEquals(AuditFindingStatus.REMEDIATED,
                asOrg(ORG_PAY, () -> findingService.remediate(fid, "a").getStatus()));
        assertEquals(AuditFindingStatus.CLOSED,
                asOrg(ORG_PAY, () -> findingService.closeFinding(fid, "a").getStatus()));
    }

    // ---------- 组织隔离 ----------

    @Test
    void 组织隔离_org12的审计发现org13看不到() {
        Long pid = asOrg(ORG_PAY, () ->
                planService.create(ORG_PAY, "支付外审", AuditType.EXTERNAL, TODAY.plusDays(30), "c").getId());
        asOrg(ORG_PAY, () -> findingService.createFinding(ORG_PAY, pid, "仅支付可见", AuditSeverity.HIGH, "a"));

        List<AuditFinding> payView = asOrg(ORG_PAY, () -> findingService.listByPlan(pid));
        assertEquals(1, payView.size(), "org12 应看到自己的 1 条审计发现");

        List<AuditFinding> cfView = asOrg(ORG_CF, () -> findingService.listByPlan(pid));
        assertTrue(cfView.isEmpty(), "org13 不应看到 org12 的审计发现");
    }

    // ---------- 留痕 ----------

    @Test
    void 留痕_漏斗推进后哈希链校验通过且有记录() {
        Long pid = asOrg(ORG_PAY, () ->
                planService.create(ORG_PAY, "留痕外审", AuditType.EXTERNAL, TODAY.plusDays(30), "c").getId()); // PLAN_CREATE=1
        Long fid = asOrg(ORG_PAY, () ->
                findingService.createFinding(ORG_PAY, pid, "f", AuditSeverity.HIGH, "a").getId());            // FINDING_CREATE=2
        asOrg(ORG_PAY, () -> findingService.submitResponse(fid, "a"));   // FUNNEL=3
        asOrg(ORG_PAY, () -> findingService.acceptResponse(fid, "a"));   // FUNNEL=4
        asOrg(ORG_PAY, () -> findingService.confirmClose(fid, "a"));     // FUNNEL=5

        ChainVerifyResult r = asOrg(ORG_PAY, () -> hashChainService.verify(ORG_PAY));
        assertTrue(r.valid(), "留痕后链应校验通过");
        assertEquals(5, r.count(), "应有 5 条 M3 操作留痕");
    }

    // ---------- 调度兼容（扩展 audit_plan 后 ExpiryScanService 仍正常） ----------

    @Test
    void 调度兼容_扩展后仍对EXTERNAL计划产临近事件() throws Exception {
        // 经 M3 Service 新建外审计划：plan_start_date = today+10，reminder_days 由 V3 库级默认 {15,10} 填充
        // → 今天恰好命中 10 天提醒；external_status 默认 'PLANNED' 满足扫描条件。
        asOrg(ORG_PAY, () ->
                planService.create(ORG_PAY, "支付-年度外审", AuditType.EXTERNAL, TODAY.plusDays(10), "creator"));

        // 内核为系统级 actor（不在 modules 包，不受用户切面约束），直接 scanOnce。
        int emitted = scanService.scanOnce(TODAY).emitted();
        assertEquals(1, emitted, "扩展 audit_plan 后仍应对 EXTERNAL 计划产 1 条临近事件");
        assertEquals(1, countEvents("EXT_AUDIT_PLAN_APPROACHING"), "domain_event 应有 1 条");
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
