package com.mandao.grc;

import com.mandao.grc.common.isolation.IsolationContext;
import com.mandao.grc.modules.audit.management.AuditFindingService;
import com.mandao.grc.modules.audit.management.AuditPlanService;
import com.mandao.grc.modules.audit.management.AuditSeverity;
import com.mandao.grc.modules.audit.management.AuditType;
import com.mandao.grc.modules.audit.management.RemediationService;
import com.mandao.grc.modules.regulatory.CompliancePlanService;
import com.mandao.grc.modules.regulatory.RegFilingService;
import com.mandao.grc.modules.workbench.NotificationView;
import com.mandao.grc.modules.workbench.TodoItem;
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
import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 工作台（我的待办 + 通知中心）集成测试（真实 PG + 切面 + RLS）。验证：
 *  1) 待办：跨模块归并（未验证整改 / 未完成合规项 / 待报送）各 1，且按域隔离；
 *  2) 通知：调度内核 reminder_dispatch_log 经可见组织过滤返回；org13 看不到 org12 的提醒。
 *
 * 设计依据：需求「我的待办 / 通知中心」、调度内核、D2-5。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "grc.scheduler.enabled=false")
@Testcontainers
class WorkbenchTest {

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

    @Autowired private WorkbenchService workbenchService;
    @Autowired private RemediationService remediationService;
    @Autowired private AuditPlanService auditPlanService;
    @Autowired private AuditFindingService auditFindingService;
    @Autowired private CompliancePlanService compliancePlanService;
    @Autowired private RegFilingService regFilingService;

    private static final long ORG_PAY = 12L;
    private static final long ORG_CF = 13L;
    private static final LocalDate TODAY = LocalDate.of(2026, 6, 24);

    @BeforeEach
    void clean() throws Exception {
        execAsOwner("TRUNCATE remediation_order, audit_finding, audit_plan, compliance_plan_item, compliance_plan, "
                + "reg_filing, reminder_dispatch_log, operation_log RESTART IDENTITY CASCADE");
    }

    @AfterEach
    void clearContext() {
        IsolationContext.clear();
    }

    @Test
    void 待办_跨模块归并且按域隔离() {
        asOrg(ORG_PAY, () -> {
            Long pid = auditPlanService.create(ORG_PAY, "内审", AuditType.INTERNAL, TODAY.plusDays(10), "c").getId();
            Long fid = auditFindingService.createFinding(ORG_PAY, pid, "缺陷", AuditSeverity.HIGH, "a").getId();
            remediationService.create(fid, "owner", TODAY.plusDays(7), "整改措施", "lead");
            Long cpid = compliancePlanService.create(ORG_PAY, 2026, "计划", null, "c").getId();
            compliancePlanService.addItem(cpid, "反洗钱报送", "合规部", TODAY.plusDays(30), "c");
            regFilingService.create(ORG_PAY, "反洗钱报表", "央行", TODAY.plusDays(20), "c");
            return null;
        });

        List<TodoItem> todos = asOrg(ORG_PAY, () -> workbenchService.todos());
        assertEquals(3, todos.size(), "应归并 3 条待办（整改/合规项/报送各 1）");
        assertTrue(todos.stream().anyMatch(t -> t.type().equals("REMEDIATION")));
        assertTrue(todos.stream().anyMatch(t -> t.type().equals("COMPLIANCE_ITEM")));
        assertTrue(todos.stream().anyMatch(t -> t.type().equals("REG_FILING")));

        assertTrue(asOrg(ORG_CF, () -> workbenchService.todos()).isEmpty(), "org13 不应看到 org12 的待办");
    }

    @Test
    void 通知_按可见组织过滤() throws Exception {
        // 调度内核派发的提醒（直接以 owner 写入 reminder_dispatch_log，模拟内核产出）
        execAsOwner("INSERT INTO reminder_dispatch_log(object_type,object_id,event_type,threshold_key,org_id) "
                + "VALUES ('REG_FILING',5,'REG_FILING_DUE','reminder_day=10',12)");
        execAsOwner("INSERT INTO reminder_dispatch_log(object_type,object_id,event_type,threshold_key,org_id) "
                + "VALUES ('AUDIT_PLAN',9,'EXT_AUDIT_PLAN_APPROACHING','reminder_day=15',13)");

        List<NotificationView> pay = asOrg(ORG_PAY, () -> workbenchService.notifications(null));
        assertEquals(1, pay.size(), "org12 仅应看到自己的 1 条提醒");
        assertEquals("REG_FILING_DUE", pay.get(0).eventType());

        List<NotificationView> cf = asOrg(ORG_CF, () -> workbenchService.notifications(null));
        assertEquals(1, cf.size(), "org13 仅应看到自己的 1 条提醒");
        assertEquals("EXT_AUDIT_PLAN_APPROACHING", cf.get(0).eventType());
    }

    // ---------- 测试辅助 ----------

    private void execAsOwner(String sql) throws Exception {
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement()) {
            s.executeUpdate(sql);
        }
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
