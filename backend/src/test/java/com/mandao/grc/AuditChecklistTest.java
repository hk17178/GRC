package com.mandao.grc;

import com.mandao.grc.common.isolation.IsolationContext;
import com.mandao.grc.modules.assessment.Assessment;
import com.mandao.grc.modules.assessment.AssessmentService;
import com.mandao.grc.modules.audit.management.AuditPlan;
import com.mandao.grc.modules.audit.management.AuditPlanService;
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
 * 内部审计检查表接表单引擎集成测试（V40）。验证：
 *  1) 绑定模板 → 执行检查表 → 生成评估（标题带计划名、挂模板 id）并回填 checklist_assessment_id；
 *  2) 幂等：重复执行返回同一评估，不重复生成；
 *  3) 门控：未绑定模板执行抛 IllegalStateException；已执行后换绑抛 IllegalStateException。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "grc.scheduler.enabled=false")
@Testcontainers
class AuditChecklistTest {

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
    private AssessmentService assessmentService;

    private static final long ORG_PAY = 12L;

    @BeforeEach
    void clean() throws Exception {
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement()) {
            s.executeUpdate("TRUNCATE audit_plan, operation_log RESTART IDENTITY CASCADE");
        }
    }

    @AfterEach
    void clearContext() {
        IsolationContext.clear();
    }

    @Test
    void 绑定执行_生成评估_幂等() {
        AuditPlan plan = asOrg(ORG_PAY, () ->
                planService.create(ORG_PAY, "等保内审", AuditType.INTERNAL, LocalDate.now(), "c"));

        asOrg(ORG_PAY, () -> planService.bindChecklist(plan.getId(), 42L, "c"));
        AuditPlan done = asOrg(ORG_PAY, () -> planService.startChecklist(plan.getId(), "c"));
        assertNotNull(done.getChecklistAssessmentId(), "应回填检查表评估 id");

        Assessment a = asOrg(ORG_PAY, () -> assessmentService.get(done.getChecklistAssessmentId()));
        assertEquals("审计检查表 · 等保内审", a.getTitle());
        assertEquals(42L, a.getTemplateId(), "评估应挂检查表模板");

        // 幂等：再次执行返回同一评估
        AuditPlan again = asOrg(ORG_PAY, () -> planService.startChecklist(plan.getId(), "c"));
        assertEquals(done.getChecklistAssessmentId(), again.getChecklistAssessmentId(), "重复执行不重复生成");
    }

    @Test
    void 未绑定执行与已执行换绑_均被拒() {
        AuditPlan plan = asOrg(ORG_PAY, () ->
                planService.create(ORG_PAY, "ISO 内审", AuditType.INTERNAL, LocalDate.now(), "c"));

        assertThrows(IllegalStateException.class,
                () -> asOrg(ORG_PAY, () -> planService.startChecklist(plan.getId(), "c")),
                "未绑定模板不允许执行");

        asOrg(ORG_PAY, () -> planService.bindChecklist(plan.getId(), 7L, "c"));
        asOrg(ORG_PAY, () -> planService.startChecklist(plan.getId(), "c"));
        assertThrows(IllegalStateException.class,
                () -> asOrg(ORG_PAY, () -> planService.bindChecklist(plan.getId(), 8L, "c")),
                "已执行后不允许换绑模板");
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
