package com.mandao.grc;

import com.mandao.grc.common.isolation.IsolationContext;
import com.mandao.grc.modules.audit.management.AuditAnnualItem;
import com.mandao.grc.modules.audit.management.AuditAnnualPlan;
import com.mandao.grc.modules.audit.management.AuditAnnualService;
import com.mandao.grc.modules.audit.management.AuditPlan;
import com.mandao.grc.modules.audit.management.AuditPlanService;
import com.mandao.grc.modules.audit.management.AuditReportService;
import com.mandao.grc.modules.audit.management.AuditType;
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
import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 审计 A3 集成测试（V52）：年度计划层 + follow-up + 文书套打。验证：
 *  1) 年度计划 建→对象入清单→批准冻结→逐项立项（回填 plan_id，防重复立项）；未批准不可立项；
 *  2) follow-up：原计划须 CLOSED；新计划 follow_up_of 关联原计划；
 *  3) 通知书/报告 docx 套打产出非空（PK 头）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "grc.scheduler.enabled=false")
@Testcontainers
class AuditAnnualTest {

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
    private AuditAnnualService annualService;
    @Autowired
    private AuditPlanService planService;
    @Autowired
    private AuditReportService reportService;

    private static final long ORG_PAY = 12L;

    @BeforeEach
    void clean() throws Exception {
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement()) {
            s.executeUpdate("TRUNCATE audit_annual_item, audit_annual_plan, audit_procedure, audit_report, "
                    + "remediation_order, audit_finding, audit_plan, operation_log CASCADE");
        }
    }

    @AfterEach
    void clearContext() {
        IsolationContext.clear();
    }

    @Test
    void 年度计划_批准冻结_立项防重复() {
        AuditAnnualPlan annual = asOrg(ORG_PAY, () -> annualService.create(ORG_PAY, 2026, null, "cae"));
        assertEquals("2026 年度内部审计计划", annual.getTitle());

        AuditAnnualItem item = asOrg(ORG_PAY, () ->
                annualService.addItem(annual.getId(), "支付结算系统", 1, "Q3", "备付金合规重点", "cae"));

        // 未批准不可立项
        assertThrows(IllegalStateException.class, () -> asOrg(ORG_PAY, () ->
                annualService.toPlan(item.getId(), LocalDate.now(), "cae")));

        asOrg(ORG_PAY, () -> annualService.approve(annual.getId(), "cae"));
        // 批准后清单冻结
        assertThrows(IllegalStateException.class, () -> asOrg(ORG_PAY, () ->
                annualService.addItem(annual.getId(), "再加对象", 2, "Q4", null, "cae")));

        AuditAnnualItem linked = asOrg(ORG_PAY, () ->
                annualService.toPlan(item.getId(), LocalDate.of(2026, 8, 1), "cae"));
        assertNotNull(linked.getPlanId(), "立项应回填 plan_id");
        // 防重复立项
        assertThrows(IllegalStateException.class, () -> asOrg(ORG_PAY, () ->
                annualService.toPlan(item.getId(), LocalDate.now(), "cae")));
    }

    @Test
    void followUp_原计划须关闭_关联溯源() {
        AuditPlan origin = asOrg(ORG_PAY, () ->
                planService.create(ORG_PAY, "权限年审", AuditType.INTERNAL, LocalDate.now(), "c"));
        // 未关闭不可 follow-up
        assertThrows(IllegalStateException.class, () -> asOrg(ORG_PAY, () ->
                annualService.followUp(origin.getId(), null, "c")));

        asOrg(ORG_PAY, () -> planService.start(origin.getId(), "c"));
        asOrg(ORG_PAY, () -> planService.report(origin.getId(), "c"));
        asOrg(ORG_PAY, () -> planService.close(origin.getId(), "c"));

        AuditPlan follow = asOrg(ORG_PAY, () -> annualService.followUp(origin.getId(), LocalDate.now(), "c"));
        assertEquals(origin.getId(), follow.getFollowUpOf(), "follow-up 应关联原计划");
        assertTrue(follow.getTitle().startsWith("后续审计"));
    }

    @Test
    void 文书套打_通知书与报告docx() {
        AuditPlan plan = asOrg(ORG_PAY, () ->
                planService.create(ORG_PAY, "数据安全审计", AuditType.INTERNAL, LocalDate.now(), "c"));
        asOrg(ORG_PAY, () -> planService.saveNotice(plan.getId(), "数据管理部", "数据分级与出境",
                "2026 年度计划", "张三、李四", true, "cae"));

        byte[] notice = asOrg(ORG_PAY, () -> reportService.buildNoticeDocx(plan.getId()));
        assertTrue(notice.length > 1000, "通知书 docx 应非空");
        assertEquals(0x50, notice[0]);
        assertEquals(0x4B, notice[1]);

        var report = asOrg(ORG_PAY, () -> reportService.createDraft(plan.getId(), "auditor"));
        byte[] rdocx = asOrg(ORG_PAY, () -> reportService.buildReportDocx(report.getId()));
        assertTrue(rdocx.length > 1000, "报告 docx 应非空");
        assertEquals(0x50, rdocx[0]);
        assertEquals(0x4B, rdocx[1]);
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
