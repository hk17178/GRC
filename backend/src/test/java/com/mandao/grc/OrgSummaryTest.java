package com.mandao.grc;

import com.mandao.grc.common.isolation.IsolationContext;
import com.mandao.grc.modules.dashboard.OrgSummaryService;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 按组织聚合态势（驾驶舱热力矩阵/整改真值）集成测试。验证：
 *  1) 六域计数为真值：种入 org12 一条 OPEN 风险发现 + 一条含 PI 资产 → risk=1、data=1；
 *  2) 整改完成率：org12 两单一 VERIFIED 一 PENDING 且逾期 → pct=50、overdue=1；
 *  3) 隔离：以 org13 身份调用，行集不含 org12（RLS 裁剪 + visible_orgs 行基准）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "grc.scheduler.enabled=false")
@Testcontainers
class OrgSummaryTest {

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
    private OrgSummaryService service;

    private static final long ORG_PAY = 12L;
    private static final long ORG_CF = 13L;

    @BeforeEach
    void seed() throws Exception {
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement()) {
            s.executeUpdate("TRUNCATE remediation_order, audit_finding, audit_plan, asset, risk_finding, assessment, reminder_dispatch_log RESTART IDENTITY CASCADE");
            // org12：1 条 OPEN 风险发现（挂在一次评估下）+ 1 条含 PI 资产 + 2 张整改单（1 VERIFIED / 1 PENDING 逾期）
            s.executeUpdate("INSERT INTO assessment(id, org_id, title, status) VALUES (9001, 12, 'T', 'DRAFT')");
            s.executeUpdate("INSERT INTO risk_finding(org_id, assessment_id, title, status) "
                    + "VALUES (12, 9001, 'F', 'OPEN')");
            s.executeUpdate("INSERT INTO asset(org_id, name, contains_pi) VALUES (12, 'CRM', true)");
            s.executeUpdate("INSERT INTO audit_plan(org_id, title, plan_start_date, audit_type) "
                    + "VALUES (12, 'AP', CURRENT_DATE, 'INTERNAL')");
            s.executeUpdate("INSERT INTO audit_finding(org_id, audit_plan_id, title, severity, status) "
                    + "VALUES (12, (SELECT max(id) FROM audit_plan), 'AF', 'HIGH', 'OPEN')");
            s.executeUpdate("INSERT INTO remediation_order(org_id, finding_id, status) "
                    + "VALUES (12, (SELECT max(id) FROM audit_finding), 'VERIFIED')");
            s.executeUpdate("INSERT INTO remediation_order(org_id, finding_id, status, due_date) "
                    + "VALUES (12, (SELECT max(id) FROM audit_finding), 'PENDING', CURRENT_DATE - 3)");
        }
    }

    @AfterEach
    void clearContext() {
        IsolationContext.clear();
    }

    @Test
    void 六域计数与整改完成率_为真值() {
        List<OrgSummaryService.OrgRow> rows = asOrg(ORG_PAY, service::orgSummary);
        OrgSummaryService.OrgRow r = rows.stream().filter(x -> x.orgId() == ORG_PAY).findFirst().orElseThrow();
        assertEquals(1, r.risk(), "OPEN 风险发现 1 条");
        assertEquals(1, r.data(), "含 PI 资产 1 条");
        assertEquals(1, r.audit(), "未闭环审计发现 1 条");
        assertEquals(1, r.remed(), "未验证整改单 1 张");
        assertEquals(2, r.remedTotal());
        assertEquals(1, r.remedDone());
        assertEquals(1, r.remedOverdue(), "过期未验证 1 张");
        assertEquals(50, r.remedPct());
    }

    @Test
    void 隔离_org13行集不含org12() {
        List<OrgSummaryService.OrgRow> rows = asOrg(ORG_CF, service::orgSummary);
        assertTrue(rows.stream().noneMatch(x -> x.orgId() == ORG_PAY), "org13 视角不应出现 org12 行");
    }

    // ---------- B37 跨子公司 benchmark + 回执口径成文 ----------

    @Test
    void b37_benchmark_排名均值偏差与回执率() throws Exception {
        // 追加 org13 一条 OPEN 风险发现（负荷=1）+ 两组织的回执台账
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement()) {
            s.executeUpdate("INSERT INTO assessment(id, org_id, title, status) VALUES (9013, 13, 'T13', 'DRAFT')");
            s.executeUpdate("INSERT INTO risk_finding(org_id, assessment_id, title, status) VALUES (13, 9013, 'F13', 'OPEN')");
            // org12 台账：4 条，2 条已回执（read_by 落人）→ 回执率 50%
            for (int i = 1; i <= 4; i++) {
                s.executeUpdate("INSERT INTO reminder_dispatch_log(org_id, object_type, object_id, event_type, threshold_key"
                        + (i <= 2 ? ", read_by, read_at" : "") + ") VALUES (12, 'X', " + i + ", 'E', 'k12-" + i + "'"
                        + (i <= 2 ? ", 'u', now()" : "") + ")");
            }
            // org13 台账：2 条，全部已回执 → 回执率 100%
            for (int i = 1; i <= 2; i++) {
                s.executeUpdate("INSERT INTO reminder_dispatch_log(org_id, object_type, object_id, event_type, threshold_key, read_by, read_at) "
                        + "VALUES (13, 'X', " + i + ", 'E', 'k13-" + i + "', 'u', now())");
            }
        }

        // 集团视角（可见 1/12/13）
        OrgSummaryService.Benchmark bm = asOrgs(List.of(1L, ORG_PAY, ORG_CF), service::benchmark);
        // org12 负荷=risk1+data1+audit1+remed1=4；org13=1；org1=0 → 均值 round(5/3)=2
        assertEquals(2, bm.avgLoad());
        var pay = bm.rows().stream().filter(r -> r.orgId() == ORG_PAY).findFirst().orElseThrow();
        assertEquals(1, pay.rank(), "负荷最重者排第 1");
        assertEquals(4, pay.load());
        assertEquals(2, pay.deltaVsAvg(), "对均值偏差 4-2=2");
        assertEquals(4, pay.ackTotal());
        assertEquals(2, pay.ackDone());
        assertEquals(50, pay.ackRatePct(), "org12 回执率 50%");
        var cf = bm.rows().stream().filter(r -> r.orgId() == ORG_CF).findFirst().orElseThrow();
        assertEquals(100, cf.ackRatePct(), "org13 回执率 100%");
        assertTrue(pay.rank() < cf.rank(), "org12 负荷更重，排名靠前");
    }

    @Test
    void b37_benchmark隔离_子公司只见自身且回执不跨域() throws Exception {
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement()) {
            // org12 台账 1 条（org13 视角不得计入）
            s.executeUpdate("INSERT INTO reminder_dispatch_log(org_id, object_type, object_id, event_type, threshold_key) "
                    + "VALUES (12, 'X', 1, 'E', 'iso-12')");
        }
        // org13 单组织视角：benchmark 仅一行，且不含 org12；回执台账也不跨域
        OrgSummaryService.Benchmark bm = asOrgs(List.of(ORG_CF), service::benchmark);
        assertTrue(bm.rows().stream().noneMatch(r -> r.orgId() == ORG_PAY), "子公司 benchmark 不得出现 org12");
        assertTrue(bm.rows().stream().allMatch(r -> r.orgId() == ORG_CF), "只应见自身");
    }

    private <T> T asOrg(long orgId, Supplier<T> action) {
        return asOrgs(List.of(orgId), action);
    }

    private <T> T asOrgs(List<Long> orgIds, Supplier<T> action) {
        IsolationContext.set(orgIds);
        try {
            return action.get();
        } finally {
            IsolationContext.clear();
        }
    }
}
