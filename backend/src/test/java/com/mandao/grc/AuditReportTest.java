package com.mandao.grc;

import com.mandao.grc.common.isolation.IsolationContext;
import com.mandao.grc.modules.audit.management.AuditFinding;
import com.mandao.grc.modules.audit.management.AuditFindingService;
import com.mandao.grc.modules.audit.management.AuditOpinion;
import com.mandao.grc.modules.audit.management.AuditPlan;
import com.mandao.grc.modules.audit.management.AuditPlanService;
import com.mandao.grc.modules.audit.management.AuditReport;
import com.mandao.grc.modules.audit.management.AuditReportService;
import com.mandao.grc.modules.audit.management.AuditSeverity;
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
 * 审计 A1 集成测试（V47）：发现五要素 + 审计报告生命周期。验证：
 *  1) 五要素补全与管理层回应落库（回应人/时间）；
 *  2) 报告自动组稿：正文含发现五要素与整改台账；幂等（重复生成返回既有）；
 *  3) 生命周期：DRAFT→COMMENTING→FINAL→ISSUED；未选意见不得定稿；签发后不可编辑。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "grc.scheduler.enabled=false")
@Testcontainers
class AuditReportTest {

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
    private AuditReportService reportService;

    private static final long ORG_PAY = 12L;

    @BeforeEach
    void clean() throws Exception {
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement()) {
            s.executeUpdate("TRUNCATE audit_report, remediation_order, audit_finding, audit_plan, operation_log CASCADE");
        }
    }

    @AfterEach
    void clearContext() {
        IsolationContext.clear();
    }

    @Test
    void 五要素与管理层回应_落库() {
        AuditPlan plan = asOrg(ORG_PAY, () ->
                planService.create(ORG_PAY, "权限内审", AuditType.INTERNAL, LocalDate.now(), "c"));
        AuditFinding f = asOrg(ORG_PAY, () ->
                findingService.createFinding(ORG_PAY, plan.getId(), "离职账号未及时禁用", AuditSeverity.HIGH, "c"));

        asOrg(ORG_PAY, () -> findingService.setDetail(f.getId(),
                "抽样 20 个离职人员中 3 人账号在离职后 30 天仍可登录",
                "《账号管理办法》第 12 条：离职当日禁用全部账号",
                "HR 离职流程与 IT 账号回收未打通，靠人工邮件通知",
                "离职人员可越权访问核心系统，存在数据泄露与舞弊风险",
                "建立 HR-IT 联动的自动禁用机制，并每月复核", "auditor"));
        AuditFinding responded = asOrg(ORG_PAY, () ->
                findingService.respond(f.getId(), "确认问题属实，Q3 完成 HR-IT 联动改造", "被审计部门-王五"));

        assertEquals("《账号管理办法》第 12 条：离职当日禁用全部账号", responded.getCriteriaDesc());
        assertEquals("确认问题属实，Q3 完成 HR-IT 联动改造", responded.getMgmtResponse());
        assertNotNull(responded.getResponseAt(), "回应时间应落库");
        assertEquals("被审计部门-王五", responded.getResponseBy());
    }

    @Test
    void 报告自动组稿_含五要素_幂等() {
        AuditPlan plan = asOrg(ORG_PAY, () ->
                planService.create(ORG_PAY, "支付系统内审", AuditType.INTERNAL, LocalDate.now(), "c"));
        AuditFinding f = asOrg(ORG_PAY, () ->
                findingService.createFinding(ORG_PAY, plan.getId(), "日志留存不足", AuditSeverity.MID, "c"));
        asOrg(ORG_PAY, () -> findingService.setDetail(f.getId(),
                "核心系统日志仅留存 90 天", "等保三级要求留存不少于 180 天",
                "存储扩容预算未批", "安全事件溯源能力不足", "扩容并调整留存策略至 180 天", "c"));

        AuditReport draft = asOrg(ORG_PAY, () -> reportService.createDraft(plan.getId(), "auditor"));
        assertEquals("DRAFT", draft.getStatus());
        assertTrue(draft.getContent().contains("日志留存不足"), "正文应含发现标题");
        assertTrue(draft.getContent().contains("等保三级要求留存不少于 180 天"), "正文应含五要素·标准");
        assertTrue(draft.getContent().contains("扩容并调整留存策略至 180 天"), "正文应含五要素·建议");

        AuditReport again = asOrg(ORG_PAY, () -> reportService.createDraft(plan.getId(), "auditor"));
        assertEquals(draft.getId(), again.getId(), "重复生成应返回既有报告（幂等）");
    }

    @Test
    void 报告生命周期_未选意见不得定稿_签发后冻结() {
        AuditPlan plan = asOrg(ORG_PAY, () ->
                planService.create(ORG_PAY, "年度内审", AuditType.INTERNAL, LocalDate.now(), "c"));
        AuditReport r = asOrg(ORG_PAY, () -> reportService.createDraft(plan.getId(), "auditor"));

        asOrg(ORG_PAY, () -> reportService.submitComment(r.getId(), "auditor"));
        // 未选意见 → 定稿被拒
        assertThrows(IllegalStateException.class,
                () -> asOrg(ORG_PAY, () -> reportService.finalizeReport(r.getId(), "auditor")));

        asOrg(ORG_PAY, () -> reportService.update(r.getId(), null, AuditOpinion.NEEDS_IMPROVEMENT,
                "内控总体有效，账号与日志管理需改进", null, "auditor"));
        asOrg(ORG_PAY, () -> reportService.finalizeReport(r.getId(), "auditor"));
        AuditReport issued = asOrg(ORG_PAY, () -> reportService.issue(r.getId(), "audit_lead"));

        assertEquals("ISSUED", issued.getStatus());
        assertEquals("audit_lead", issued.getIssuedBy());
        assertNotNull(issued.getIssuedAt());
        // 签发后编辑被拒
        assertThrows(IllegalStateException.class, () -> asOrg(ORG_PAY, () ->
                reportService.update(r.getId(), "改标题", null, null, null, "auditor")));
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
