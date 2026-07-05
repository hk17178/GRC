package com.mandao.grc;

import com.mandao.grc.common.isolation.IsolationContext;
import com.mandao.grc.modules.assessment.Assessment;
import com.mandao.grc.modules.assessment.AssessmentPlan;
import com.mandao.grc.modules.assessment.AssessmentPlanService;
import com.mandao.grc.modules.assessment.AssessmentService;
import com.mandao.grc.modules.assessment.RiskLevel;
import com.mandao.grc.modules.assessment.RiskMatrix;
import com.mandao.grc.modules.asset.Asset;
import com.mandao.grc.modules.asset.AssetClassification;
import com.mandao.grc.modules.asset.AssetService;
import com.mandao.grc.modules.atv.RiskScenario;
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
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M2 深度包一期集成测试（真实 PG + 切面 + RLS）。验证：
 *  1) C3 风险矩阵上收：Scoring/ATV 统一档位（同一 20 分两侧一致），system_setting 加载覆写；
 *  2) B47 资产合规属性深化：等保定级/测评到期/CIA/网络区域随登记与更新落库，越界拒绝；
 *  3) B43 资产变更联动：在途评估范围内资产合规属性实变 → 产 ASSET_CHANGED 提醒（幂等降噪）；
 *  4) B45 周期复评：计划启动时复制同模板上一轮定稿评估的背景（范围/依据等），起止日期不带入。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "grc.scheduler.enabled=false")
@Testcontainers
class M2DepthTest {

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
    private AssetService assetService;
    @Autowired
    private AssessmentService assessmentService;
    @Autowired
    private AssessmentPlanService planService;

    private static final long ORG_PAY = 12L;

    @BeforeEach
    void clean() throws Exception {
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement()) {
            s.executeUpdate("TRUNCATE assessment_asset, reminder_dispatch_log, domain_event CASCADE");
            s.executeUpdate("TRUNCATE risk_scenario, asset CASCADE");
            s.executeUpdate("DELETE FROM assessment WHERE org_id = 12");
        }
    }

    @AfterEach
    void clearContext() {
        IsolationContext.clear();
    }

    @Test
    void c3_风险矩阵两侧统一_20分同判极高() {
        // 收敛前：ScoringService 判 HIGH、ATV 判 VERY_HIGH；收敛后两侧都走 RiskMatrix
        assertEquals(RiskLevel.VERY_HIGH, RiskMatrix.levelOf(4, 5));       // 20
        assertEquals(RiskLevel.VERY_HIGH, RiskScenario.deriveLevel(4, 5)); // 20
        assertEquals(RiskLevel.MID, RiskMatrix.levelOf(3, 3));             // 9
        assertEquals(RiskLevel.VERY_LOW, RiskMatrix.levelOf(1, 1));        // 1
    }

    @Test
    void c3_配置覆写档位后按新档定级() {
        try {
            // 极端档：所有非满分都算低，仅 25 算极高
            RiskMatrix.configure("[{\"max\":24,\"level\":\"LOW\"},{\"max\":24,\"level\":\"LOW\"},"
                    + "{\"max\":24,\"level\":\"LOW\"},{\"max\":24,\"level\":\"LOW\"},{\"max\":25,\"level\":\"VERY_HIGH\"}]");
            assertEquals(RiskLevel.LOW, RiskMatrix.levelOf(4, 5));       // 20 → LOW（按新档）
            assertEquals(RiskLevel.VERY_HIGH, RiskMatrix.levelOf(5, 5)); // 25
            // 档数不足五 → 拒绝
            assertThrows(IllegalArgumentException.class,
                    () -> RiskMatrix.configure("[{\"max\":25,\"level\":\"VERY_HIGH\"}]"));
        } finally {
            RiskMatrix.reset();
        }
    }

    @Test
    void b47_深化合规属性落库_等保越界拒绝() {
        Asset a = asOrg(ORG_PAY, () -> assetService.register(ORG_PAY, "清算核心", "SYSTEM", "dba",
                AssetClassification.SENSITIVE, true, false, true, false, "HIGH",
                3, LocalDate.of(2027, 6, 30), "3-3-2", "生产核心区", "c"));
        assertEquals(3, a.getMlpsLevel());
        assertEquals("3-3-2", a.getCiaRating());
        assertEquals("生产核心区", a.getNetworkZone());
        assertEquals(LocalDate.of(2027, 6, 30), a.getMlpsReviewDue());

        // 等保定级越界 → 拒绝
        assertThrows(IllegalArgumentException.class, () -> asOrg(ORG_PAY, () ->
                assetService.update(a.getId(), "清算核心", "SYSTEM", "dba", AssetClassification.SENSITIVE,
                        true, false, true, false, "HIGH", 5, null, null, null, "c")));
    }

    @Test
    void b43_范围资产合规实变_向在途评估产提醒() {
        // 一个在途评估（DRAFT），把资产纳入范围
        Assessment a = asOrg(ORG_PAY, () -> assessmentService.create(ORG_PAY, "年度等保自评", null, "2026", "c"));
        Asset asset = asOrg(ORG_PAY, () -> assetService.register(ORG_PAY, "网关A", "SYSTEM", "o",
                AssetClassification.INTERNAL, false, false, false, false, "MID", "c"));
        asOrg(ORG_PAY, () -> assessmentService.addScopeAsset(a.getId(), asset.getId(), "c"));

        // 合规属性实变（含PI 由否→是）→ 应产一条 ASSET_CHANGED 提醒
        asOrg(ORG_PAY, () -> assetService.update(asset.getId(), "网关A", "SYSTEM", "o",
                AssetClassification.SENSITIVE, true, false, false, false, "MID", "c"));
        assertEquals(1, countReminder("ASSESSMENT", a.getId(), "ASSET_CHANGED"),
                "范围内资产合规实变应向在途评估产一条提醒");

        // 同日再次实变 → 幂等（threshold_key 含日期，同日至多一条）
        asOrg(ORG_PAY, () -> assetService.update(asset.getId(), "网关A", "SYSTEM", "o",
                AssetClassification.SENSITIVE, true, true, false, false, "MID", "c"));
        assertEquals(1, countReminder("ASSESSMENT", a.getId(), "ASSET_CHANGED"),
                "同日重复变更不应重复产提醒（降噪）");
    }

    @Test
    void b45_周期复评_复制上一轮背景() {
        // 第一轮：定稿一份带背景的评估（模板 #77 虚拟引用）
        long tpl = 77L;
        Assessment prev = asOrg(ORG_PAY, () -> assessmentService.create(ORG_PAY, "第一轮", null, "2025", tpl, "c"));
        asOrg(ORG_PAY, () -> assessmentService.setContext(prev.getId(),
                "支付清算全链路", "满足等保2.0三级", "GB/T 22239-2019", "访谈+核查+测试",
                "残余高须签批", "安全部+科技部", LocalDate.of(2025, 1, 1), LocalDate.of(2025, 3, 1), "c"));
        // 推进到 COMPLETED
        asOrg(ORG_PAY, () -> assessmentService.start(prev.getId(), "c"));
        asOrg(ORG_PAY, () -> assessmentService.submitForReview(prev.getId(), "c"));
        asOrg(ORG_PAY, () -> assessmentService.complete(prev.getId(), "c"));

        // 第二轮：计划启动应把上一轮背景带入新草稿
        AssessmentPlan plan = asOrg(ORG_PAY, () ->
                planService.create(ORG_PAY, "第二轮复评", "ANNUAL", LocalDate.of(2026, 1, 1), tpl));
        AssessmentPlan started = asOrg(ORG_PAY, () -> planService.start(plan.getId(), "c"));
        Assessment fresh = asOrg(ORG_PAY, () -> assessmentService.get(started.getAssessmentId()));

        assertEquals("支付清算全链路", fresh.getScope());
        assertEquals("GB/T 22239-2019", fresh.getBasis());
        assertEquals("安全部+科技部", fresh.getTeam());
        // 起止日期属本轮周期，不复制
        assertNull(fresh.getStartDate(), "起止日期不应从上一轮带入");
    }

    // ---------- 辅助 ----------

    private int countReminder(String objectType, long objectId, String eventType) {
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement();
             ResultSet rs = s.executeQuery("SELECT count(*) FROM reminder_dispatch_log WHERE object_type = '"
                     + objectType + "' AND object_id = " + objectId + " AND event_type = '" + eventType + "'")) {
            rs.next();
            return rs.getInt(1);
        } catch (Exception e) {
            throw new RuntimeException(e);
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

    private void asOrg(long orgId, Runnable action) {
        IsolationContext.set(List.of(orgId));
        try {
            action.run();
        } finally {
            IsolationContext.clear();
        }
    }
}
