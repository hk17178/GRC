package com.mandao.grc;

import com.mandao.grc.common.isolation.IsolationContext;
import com.mandao.grc.modules.audit.management.AuditPlan;
import com.mandao.grc.modules.audit.management.AuditPlanService;
import com.mandao.grc.modules.audit.management.AuditProcedure;
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

/**
 * 审计 A2 集成测试（V50）：审计通知书 + 程序/工作底稿。验证：
 *  1) 通知书 保存→签发（签发落人/时间），签发后内容冻结；
 *  2) 程序底稿编号 WP-{plan}-{seq} 自动递增；执行必填记录（PENDING→DONE）且不可覆盖；
 *  3) 复核控制：仅 DONE 可复核；复核人=执行人被拒。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "grc.scheduler.enabled=false")
@Testcontainers
class AuditFieldworkTest {

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

    private static final long ORG_PAY = 12L;

    @BeforeEach
    void clean() throws Exception {
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement()) {
            s.executeUpdate("TRUNCATE audit_procedure, audit_report, remediation_order, audit_finding, audit_plan, operation_log CASCADE");
        }
    }

    @AfterEach
    void clearContext() {
        IsolationContext.clear();
    }

    @Test
    void 通知书_保存签发_签发后冻结() {
        AuditPlan plan = asOrg(ORG_PAY, () ->
                planService.create(ORG_PAY, "支付结算审计", AuditType.INTERNAL, LocalDate.now(), "c"));

        // 先保存草稿（不签发）
        asOrg(ORG_PAY, () -> planService.saveNotice(plan.getId(), "结算运营部", "结算与备付金管理",
                "2026 年度内审计划第 3 项", "张三（组长）、李四", false, "auditor"));
        // 签发
        AuditPlan issued = asOrg(ORG_PAY, () -> planService.saveNotice(plan.getId(), "结算运营部",
                "结算与备付金管理", "2026 年度内审计划第 3 项", "张三（组长）、李四", true, "audit_lead"));
        assertEquals("audit_lead", issued.getNoticeIssuedBy());
        assertNotNull(issued.getNoticeIssuedAt());

        // 签发后冻结
        assertThrows(IllegalStateException.class, () -> asOrg(ORG_PAY, () ->
                planService.saveNotice(plan.getId(), "改单位", null, null, null, false, "auditor")));
    }

    @Test
    void 程序底稿_编号递增_执行必填不可覆盖_复核控制() {
        AuditPlan plan = asOrg(ORG_PAY, () ->
                planService.create(ORG_PAY, "账号安全审计", AuditType.INTERNAL, LocalDate.now(), "c"));

        AuditProcedure p1 = asOrg(ORG_PAY, () ->
                planService.addProcedure(plan.getId(), "抽样核验离职账号禁用", "验证账号回收控制有效性", "c"));
        AuditProcedure p2 = asOrg(ORG_PAY, () ->
                planService.addProcedure(plan.getId(), "核查特权账号审批单", null, "c"));
        assertEquals("WP-" + plan.getId() + "-1", p1.getWorkpaperNo());
        assertEquals("WP-" + plan.getId() + "-2", p2.getWorkpaperNo(), "底稿编号应递增");

        // 执行必填记录
        assertThrows(IllegalArgumentException.class, () -> asOrg(ORG_PAY, () ->
                planService.executeProcedure(p1.getId(), " ", "auditor_a")));
        AuditProcedure done = asOrg(ORG_PAY, () ->
                planService.executeProcedure(p1.getId(), "抽样 20 个离职账号，3 个未禁用，详见附件清单", "auditor_a"));
        assertEquals("DONE", done.getStatus());
        // 不可覆盖
        assertThrows(IllegalStateException.class, () -> asOrg(ORG_PAY, () ->
                planService.executeProcedure(p1.getId(), "重写底稿", "auditor_a")));

        // 未执行不可复核
        assertThrows(IllegalStateException.class, () -> asOrg(ORG_PAY, () ->
                planService.reviewProcedure(p2.getId(), "reviewer_b")));
        // 复核人=执行人被拒
        assertThrows(IllegalStateException.class, () -> asOrg(ORG_PAY, () ->
                planService.reviewProcedure(p1.getId(), "auditor_a")));
        // 正常复核
        AuditProcedure reviewed = asOrg(ORG_PAY, () -> planService.reviewProcedure(p1.getId(), "reviewer_b"));
        assertEquals("REVIEWED", reviewed.getStatus());
        assertEquals("reviewer_b", reviewed.getReviewer());
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
