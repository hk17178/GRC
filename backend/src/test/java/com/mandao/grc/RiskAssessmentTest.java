package com.mandao.grc;

import com.mandao.grc.common.isolation.IsolationContext;
import com.mandao.grc.modules.assessment.Assessment;
import com.mandao.grc.modules.assessment.AssessmentService;
import com.mandao.grc.modules.assessment.AssessmentStatus;
import com.mandao.grc.modules.assessment.RiskCloseGateException;
import com.mandao.grc.modules.assessment.RiskFinding;
import com.mandao.grc.modules.assessment.RiskFindingService;
import com.mandao.grc.modules.assessment.RiskFindingStatus;
import com.mandao.grc.modules.assessment.RiskLevel;
import com.mandao.grc.modules.audit.ChainVerifyResult;
import com.mandao.grc.modules.audit.HashChainService;
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
import java.util.concurrent.Callable;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M2 风险评估集成测试（真实 PG + 应用切面 + RLS）。验证：
 *  1) 评估生命周期 DRAFT→IN_PROGRESS→PENDING_REVIEW→COMPLETED 通过，非法流转被拒；
 *  2) 【关闭门控 CR-002 红线】高残余(HIGH/VERY_HIGH)风险发现无 acceptance 关闭被拒、补 acceptance 后可关闭；
 *  3) 低残余风险发现可直接关闭（不受门控约束）；
 *  4) 组织隔离：org12 的风险发现，在 org13 上下文中看不到；
 *  5) 留痕：流转/接受后对应 org 哈希链 verify 通过且有记录。
 *
 * 设计依据：D1-2（评估生命周期、风险发现、关闭门控）、D1-3 §5.1/§8、D2-5。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "grc.scheduler.enabled=false")
@Testcontainers
class RiskAssessmentTest {

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
    private AssessmentService assessmentService;

    @Autowired
    private RiskFindingService findingService;

    @Autowired
    private HashChainService hashChainService;

    private static final long ORG_PAY = 12L;   // 支付子公司
    private static final long ORG_CF = 13L;     // 消费金融

    /**
     * 每用例前清空 M2 相关表与操作日志（owner 连接，绕 RLS；grc_app 无删权）。
     * 注意删除顺序/依赖：risk_acceptance 与 risk_finding 互为外键（finding.risk_acceptance_id），
     * 故用 CASCADE 一并清。assessment 含 V1 手工种子，TRUNCATE 后由 V5 序列从 1000 继续发号——
     * 此处保留种子行，仅清 M2 新增表与日志，避免污染其它依赖种子的测试。
     */
    @BeforeEach
    void clean() throws Exception {
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement()) {
            s.executeUpdate("TRUNCATE risk_acceptance, risk_finding, operation_log RESTART IDENTITY CASCADE");
            // 清除 Service 新建的评估（id >= 1000），保留 V1 手工种子（101/102/201/202）。
            s.executeUpdate("DELETE FROM assessment WHERE id >= 1000");
        }
    }

    @AfterEach
    void clearContext() {
        IsolationContext.clear();
    }

    // ---------- 评估生命周期 ----------

    @Test
    void 评估生命周期_草稿到完成全程通过() {
        Long id = asOrg(ORG_PAY, () ->
                assessmentService.create(ORG_PAY, "2026Q2 等保自评", "assessor1", "2026Q2", "creator").getId());

        assertEquals(AssessmentStatus.IN_PROGRESS,
                asOrg(ORG_PAY, () -> assessmentService.start(id, "assessor1").getStatus()));
        assertEquals(AssessmentStatus.PENDING_REVIEW,
                asOrg(ORG_PAY, () -> assessmentService.submitForReview(id, "assessor1").getStatus()));
        assertEquals(AssessmentStatus.COMPLETED,
                asOrg(ORG_PAY, () -> assessmentService.complete(id, "reviewer").getStatus()));
    }

    @Test
    void 评估非法流转_草稿直接完成被拒() {
        Long id = asOrg(ORG_PAY, () ->
                assessmentService.create(ORG_PAY, "X", "a", "2026Q2", "creator").getId());
        // DRAFT 不可直接 complete（complete 仅允许从 PENDING_REVIEW）
        assertThrows(IllegalStateException.class,
                () -> runAsOrg(ORG_PAY, () -> assessmentService.complete(id, "reviewer")));
    }

    // ---------- 关闭门控（CR-002 红线，核心） ----------

    @Test
    void 关闭门控_高残余无接受关闭被拒_补接受后可关闭() {
        Long aid = asOrg(ORG_PAY, () ->
                assessmentService.create(ORG_PAY, "门控评估", "a", "2026Q2", "creator").getId());

        // 新建高残余风险发现：固有 VERY_HIGH，处置后残余仍 HIGH
        Long fid = asOrg(ORG_PAY, () ->
                findingService.createFinding(ORG_PAY, aid, "核心系统弱口令", RiskLevel.VERY_HIGH, "assessor1").getId());
        asOrg(ORG_PAY, () -> findingService.setTreatment(fid, "强制改密+MFA", "assessor1"));
        asOrg(ORG_PAY, () -> findingService.setResidual(fid, RiskLevel.HIGH, "assessor1"));

        // 红线：高残余且无 acceptance → 关闭(DONE)被拒
        assertThrows(RiskCloseGateException.class,
                () -> runAsOrg(ORG_PAY, () -> findingService.close(fid, false, "assessor1")));

        // 补登记风险接受（回填 risk_acceptance_id）
        asOrg(ORG_PAY, () -> findingService.accept(fid, "ciso", "残余可接受，纳入持续监控", "ciso"));

        // 放行：现可关闭到 DONE
        assertEquals(RiskFindingStatus.DONE,
                asOrg(ORG_PAY, () -> findingService.close(fid, false, "assessor1").getStatus()));
        // 进一步可验证到 VERIFIED（门控同样放行）
        assertEquals(RiskFindingStatus.VERIFIED,
                asOrg(ORG_PAY, () -> findingService.close(fid, true, "reviewer").getStatus()));

        RiskFinding f = asOrg(ORG_PAY, () -> findingService.get(fid));
        assertNotNull(f.getRiskAcceptanceId(), "接受后应回填 risk_acceptance_id");
    }

    @Test
    void 关闭门控_极高残余无接受验证也被拒() {
        Long aid = asOrg(ORG_PAY, () ->
                assessmentService.create(ORG_PAY, "门控评估2", "a", "2026Q2", "creator").getId());
        Long fid = asOrg(ORG_PAY, () ->
                findingService.createFinding(ORG_PAY, aid, "数据出境无评估", RiskLevel.VERY_HIGH, "a").getId());
        asOrg(ORG_PAY, () -> findingService.setResidual(fid, RiskLevel.VERY_HIGH, "a"));

        // 残余 VERY_HIGH 无接受 → 关闭被拒
        assertThrows(RiskCloseGateException.class,
                () -> runAsOrg(ORG_PAY, () -> findingService.close(fid, false, "a")));
    }

    @Test
    void 关闭门控_低残余可直接关闭() {
        Long aid = asOrg(ORG_PAY, () ->
                assessmentService.create(ORG_PAY, "普通评估", "a", "2026Q2", "creator").getId());
        Long fid = asOrg(ORG_PAY, () ->
                findingService.createFinding(ORG_PAY, aid, "日志留存不足", RiskLevel.MID, "a").getId());
        asOrg(ORG_PAY, () -> findingService.setResidual(fid, RiskLevel.LOW, "a"));

        // 低残余(LOW)不受门控约束，可直接关闭，无需 acceptance
        assertEquals(RiskFindingStatus.DONE,
                asOrg(ORG_PAY, () -> findingService.close(fid, false, "a").getStatus()));
    }

    // ---------- 组织隔离 ----------

    @Test
    void 组织隔离_org12的风险发现org13看不到() {
        Long aid = asOrg(ORG_PAY, () ->
                assessmentService.create(ORG_PAY, "支付评估", "a", "2026Q2", "creator").getId());
        asOrg(ORG_PAY, () -> findingService.createFinding(ORG_PAY, aid, "仅支付可见", RiskLevel.LOW, "a"));

        // org12 上下文能看到自己的风险发现
        List<RiskFinding> payView = asOrg(ORG_PAY, () -> findingService.listByAssessment(aid));
        assertEquals(1, payView.size(), "org12 应看到自己的 1 条风险发现");

        // org13 上下文看不到 org12 的风险发现（RLS 裁剪）
        List<RiskFinding> cfView = asOrg(ORG_CF, () -> findingService.listByAssessment(aid));
        assertTrue(cfView.isEmpty(), "org13 不应看到 org12 的风险发现");
    }

    // ---------- 留痕 ----------

    @Test
    void 留痕_流转与接受后哈希链校验通过且有记录() {
        Long aid = asOrg(ORG_PAY, () ->
                assessmentService.create(ORG_PAY, "留痕评估", "a", "2026Q2", "creator").getId());
        // ASSESSMENT_CREATE = 1
        Long fid = asOrg(ORG_PAY, () ->
                findingService.createFinding(ORG_PAY, aid, "f", RiskLevel.HIGH, "a").getId()); // FINDING_CREATE = 2
        asOrg(ORG_PAY, () -> findingService.setResidual(fid, RiskLevel.HIGH, "a"));            // FINDING_RESIDUAL = 3
        asOrg(ORG_PAY, () -> findingService.accept(fid, "ciso", "ok", "ciso"));               // FINDING_ACCEPT = 4
        asOrg(ORG_PAY, () -> findingService.close(fid, false, "a"));                          // FINDING_CLOSE = 5

        ChainVerifyResult r = asOrg(ORG_PAY, () -> hashChainService.verify(ORG_PAY));
        assertTrue(r.valid(), "留痕后链应校验通过");
        assertEquals(5, r.count(), "应有 5 条 M2 操作留痕");
    }

    // ---------- 测试辅助：在指定 org 可见上下文中执行 ----------

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
}
