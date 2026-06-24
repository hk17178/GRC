package com.mandao.grc;

import com.mandao.grc.common.isolation.IsolationContext;
import com.mandao.grc.modules.assessment.AssessmentService;
import com.mandao.grc.modules.assessment.RiskFindingService;
import com.mandao.grc.modules.assessment.RiskLevel;
import com.mandao.grc.modules.audit.management.AuditFindingService;
import com.mandao.grc.modules.audit.management.AuditPlanService;
import com.mandao.grc.modules.audit.management.AuditSeverity;
import com.mandao.grc.modules.audit.management.AuditType;
import com.mandao.grc.modules.dashboard.DashboardService;
import com.mandao.grc.modules.dashboard.DashboardSummary;
import com.mandao.grc.modules.kri.KriDirection;
import com.mandao.grc.modules.kri.KriService;
import com.mandao.grc.modules.policy.PolicyService;
import com.mandao.grc.modules.regulatory.RegFilingService;
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
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 合规态势汇总集成测试（横切聚合 + RLS）。验证：
 *  1) 跨模块汇总：被门控发现 / KRI 预警 / 草稿制度 / 未关闭审计发现 / 待报送 各计 1；
 *  2) 组织隔离：org12 播种的数据，org13 视角汇总全为 0（汇总天然按域裁剪）。
 *
 * 设计依据：需求文档「合规态势」、D1-7、D2-5。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "grc.scheduler.enabled=false")
@Testcontainers
class DashboardTest {

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

    @Autowired private DashboardService dashboardService;
    @Autowired private AssessmentService assessmentService;
    @Autowired private RiskFindingService findingService;
    @Autowired private KriService kriService;
    @Autowired private PolicyService policyService;
    @Autowired private AuditPlanService auditPlanService;
    @Autowired private AuditFindingService auditFindingService;
    @Autowired private RegFilingService regFilingService;

    private static final long ORG_PAY = 12L;
    private static final long ORG_CF = 13L;
    private static final LocalDate TODAY = LocalDate.of(2026, 6, 24);

    @BeforeEach
    void clean() throws Exception {
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement()) {
            s.executeUpdate("TRUNCATE risk_acceptance, risk_finding, assessment_item, kri_measurement, kri, "
                    + "policy_signoff, policy, remediation_order, audit_finding, audit_plan, "
                    + "reg_filing, operation_log RESTART IDENTITY CASCADE");
        }
    }

    @AfterEach
    void clearContext() {
        IsolationContext.clear();
    }

    @Test
    void 合规态势_跨模块汇总并按域隔离() {
        // org12 播种：1 个被门控的风险发现
        asOrg(ORG_PAY, () -> {
            Long aid = assessmentService.create(ORG_PAY, "门控评估", "a", "2026Q2", "c").getId();
            Long fid = findingService.createFinding(ORG_PAY, aid, "高残余项", RiskLevel.VERY_HIGH, "a").getId();
            findingService.setResidual(fid, RiskLevel.VERY_HIGH, "a");
            return null;
        });
        // KRI 预警（UPPER_BAD warn5/crit10，测量 7 → WARNING）
        asOrg(ORG_PAY, () -> {
            Long kid = kriService.create(ORG_PAY, "KRI-V", "高危漏洞", "个",
                    KriDirection.UPPER_BAD, new BigDecimal("5"), new BigDecimal("10"), "sec", "c").getId();
            kriService.record(kid, new BigDecimal("7"), null, "m");
            return null;
        });
        // 草稿制度
        asOrg(ORG_PAY, () -> policyService.create(ORG_PAY, "POL-D", "草稿制度", "正文", "c"));
        // 未关闭审计发现
        asOrg(ORG_PAY, () -> {
            Long pid = auditPlanService.create(ORG_PAY, "内审", AuditType.INTERNAL, TODAY.plusDays(10), "c").getId();
            auditFindingService.createFinding(ORG_PAY, pid, "缺陷", AuditSeverity.HIGH, "a");
            return null;
        });
        // 待报送
        asOrg(ORG_PAY, () -> regFilingService.create(ORG_PAY, "反洗钱报表", "央行", TODAY.plusDays(30), "c"));

        // org12 视角：各域应各计 1
        DashboardSummary pay = asOrg(ORG_PAY, () -> dashboardService.summary());
        assertEquals(1, pay.risk().gatedFindings(), "应有 1 个被门控发现");
        assertEquals(1, pay.risk().openFindings(), "应有 1 个未关闭发现");
        assertEquals(1, pay.risk().kriWarning(), "应有 1 个 KRI 预警");
        assertEquals(1, pay.policy().draft(), "应有 1 个草稿制度");
        assertEquals(1, pay.audit().openFindings(), "应有 1 个未关闭审计发现");
        assertEquals(1, pay.regulatory().pendingFilings(), "应有 1 个待报送");

        // org13 视角：org12 的数据不可见，汇总全 0
        DashboardSummary cf = asOrg(ORG_CF, () -> dashboardService.summary());
        assertEquals(0, cf.risk().gatedFindings());
        assertEquals(0, cf.risk().kriWarning());
        assertEquals(0, cf.policy().draft());
        assertEquals(0, cf.audit().openFindings());
        assertEquals(0, cf.regulatory().pendingFilings());
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
}
