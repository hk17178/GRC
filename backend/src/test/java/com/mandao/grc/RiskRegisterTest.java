package com.mandao.grc;

import com.mandao.grc.common.isolation.IsolationContext;
import com.mandao.grc.modules.asset.Asset;
import com.mandao.grc.modules.asset.AssetService;
import com.mandao.grc.modules.assessment.Assessment;
import com.mandao.grc.modules.assessment.AssessmentService;
import com.mandao.grc.modules.assessment.RiskFinding;
import com.mandao.grc.modules.assessment.RiskLevel;
import com.mandao.grc.modules.atv.AtvService;
import com.mandao.grc.modules.atv.RiskScenario;
import com.mandao.grc.modules.atv.Threat;
import com.mandao.grc.modules.atv.Vulnerability;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 风险 R2 · 风险登记册整合集成测试（V48）。验证：
 *  1) A-T-V 场景一键生成风险发现：标题=资产：威胁（脆弱性），固有等级承接场景派生等级，携 scenarioId 溯源；
 *  2) 防重复：同一评估同一场景重复生成被拒；
 *  3) 评估范围资产：勾选（幂等）→ 清单（携资产名）→ 移除；评估完成后冻结。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "grc.scheduler.enabled=false")
@Testcontainers
class RiskRegisterTest {

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
    private AtvService atvService;
    @Autowired
    private AssetService assetService;
    @Autowired
    private AssessmentService assessmentService;
    @Autowired
    private com.mandao.grc.modules.assessment.RiskFindingService riskFindingService;

    private static final long ORG_PAY = 12L;
    private static final long ORG_CF = 13L;

    @BeforeEach
    void clean() throws Exception {
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement()) {
            s.executeUpdate("TRUNCATE assessment_asset, risk_finding, risk_scenario, threat, vulnerability, asset, assessment CASCADE");
        }
    }

    @AfterEach
    void clearContext() {
        IsolationContext.clear();
    }

    @Test
    void 场景一键生成发现_溯源_防重复() {
        Asset asset = asOrg(ORG_PAY, () -> assessmentAsset("核心支付网关"));
        Threat t = asOrg(ORG_PAY, () -> atvService.createThreat(ORG_PAY, "T1", "外部网络攻击", "网络", null, "c"));
        Vulnerability v = asOrg(ORG_PAY, () -> atvService.createVulnerability(ORG_PAY, "V1", "未打补丁", "系统", null, "c"));
        RiskScenario sc = asOrg(ORG_PAY, () ->
                atvService.createScenario(asset.getId(), t.getId(), v.getId(), 4, 5, "补丁缺失可被利用", "c"));
        Assessment a = asOrg(ORG_PAY, () -> assessmentService.create(ORG_PAY, "年度评估", "u", "2026", "c"));

        RiskFinding f = asOrg(ORG_PAY, () -> atvService.toFinding(sc.getId(), a.getId(), "c"));
        assertEquals("核心支付网关：外部网络攻击（未打补丁）", f.getTitle(), "标题应自动组装 资产：威胁（脆弱性）");
        assertEquals(sc.getInherentLevel(), f.getInherentLevel(), "固有等级承接场景派生等级");
        assertEquals(sc.getId(), f.getScenarioId(), "应携来源场景溯源");
        assertTrue(f.getInherentLevel() == RiskLevel.HIGH || f.getInherentLevel() == RiskLevel.VERY_HIGH,
                "4×5 应为高/极高档");

        // 同一评估同一场景重复生成 → 拒
        assertThrows(IllegalStateException.class,
                () -> asOrg(ORG_PAY, () -> atvService.toFinding(sc.getId(), a.getId(), "c")));
    }

    @Test
    void 范围资产_勾选幂等_清单携名_移除_完成后冻结() {
        Asset asset = asOrg(ORG_PAY, () -> assessmentAsset("CRM 系统"));
        Assessment a = asOrg(ORG_PAY, () -> assessmentService.create(ORG_PAY, "范围评估", "u", "2026", "c"));

        asOrg(ORG_PAY, () -> assessmentService.addScopeAsset(a.getId(), asset.getId(), "c"));
        asOrg(ORG_PAY, () -> assessmentService.addScopeAsset(a.getId(), asset.getId(), "c")); // 幂等
        List<AssessmentService.ScopeAssetView> list = asOrg(ORG_PAY, () -> assessmentService.listScopeAssets(a.getId()));
        assertEquals(1, list.size(), "重复勾选应幂等");
        assertEquals("CRM 系统", list.get(0).assetName(), "清单应携资产名");

        asOrg(ORG_PAY, () -> { assessmentService.removeScopeAsset(a.getId(), list.get(0).id(), "c"); return null; });
        assertTrue(asOrg(ORG_PAY, () -> assessmentService.listScopeAssets(a.getId())).isEmpty());

        // 完成后冻结
        asOrg(ORG_PAY, () -> assessmentService.start(a.getId(), "c"));
        asOrg(ORG_PAY, () -> assessmentService.submitForReview(a.getId(), "c"));
        asOrg(ORG_PAY, () -> assessmentService.complete(a.getId(), "c"));
        assertThrows(IllegalStateException.class,
                () -> asOrg(ORG_PAY, () -> assessmentService.addScopeAsset(a.getId(), asset.getId(), "c")));
    }

    // ---------- B22 加权风险指数 + 下钻 ----------

    @Test
    void b22_加权风险指数_未闭环按有效等级加权_下钻() {
        Assessment a = asOrg(ORG_PAY, () -> assessmentService.create(ORG_PAY, "指数评估", "u", "2026", "c"));
        // f1 极高(5)、f2 高(4)、f3 高但残余降到低(2)、f4 中(3)后关闭→排除
        asOrg(ORG_PAY, () -> riskFindingService.createFinding(ORG_PAY, a.getId(), "f1", RiskLevel.VERY_HIGH, "c"));
        asOrg(ORG_PAY, () -> riskFindingService.createFinding(ORG_PAY, a.getId(), "f2", RiskLevel.HIGH, "c"));
        Long f3 = asOrg(ORG_PAY, () -> riskFindingService.createFinding(ORG_PAY, a.getId(), "f3", RiskLevel.HIGH, "c").getId());
        asOrg(ORG_PAY, () -> riskFindingService.setResidual(f3, RiskLevel.LOW, "c"));   // 残余优先 → 低(2)
        Long f4 = asOrg(ORG_PAY, () -> riskFindingService.createFinding(ORG_PAY, a.getId(), "f4", RiskLevel.MID, "c").getId());
        asOrg(ORG_PAY, () -> riskFindingService.close(f4, false, "c"));                 // DONE → 排除

        var idx = asOrg(ORG_PAY, () -> riskFindingService.riskIndex());
        assertEquals(3, idx.openCount(), "未闭环 3 条（f4 已关闭排除）");
        assertEquals(5 + 4 + 2, idx.weightedScore(), "极高5+高4+低2=11");
        assertEquals(3.67, idx.avgWeight(), 0.01, "均权 11/3");
        assertEquals(1L, idx.byLevel().get("VERY_HIGH"));
        assertEquals(1L, idx.byLevel().get("HIGH"));
        assertEquals(1L, idx.byLevel().get("LOW"), "f3 按残余等级归到低");
        assertEquals(0L, idx.byLevel().get("MID"), "f4 已关闭不计");

        // 下钻：高等级下钻命中 f2（f3 已降级到低，不在高）
        var high = asOrg(ORG_PAY, () -> riskFindingService.findingsByLevel(RiskLevel.HIGH));
        assertEquals(1, high.size());
        assertEquals("f2", high.get(0).title());
    }

    @Test
    void b22_风险指数组织隔离() {
        Assessment a = asOrg(ORG_PAY, () -> assessmentService.create(ORG_PAY, "隔离评估", "u", "2026", "c"));
        asOrg(ORG_PAY, () -> riskFindingService.createFinding(ORG_PAY, a.getId(), "仅支付", RiskLevel.VERY_HIGH, "c"));
        // org13 视角：RLS 裁剪 → 指数为 0
        assertEquals(0, asOrg(ORG_CF, () -> riskFindingService.riskIndex()).openCount(), "org13 不得计入 org12 风险");
        assertEquals(1, asOrg(ORG_PAY, () -> riskFindingService.riskIndex()).openCount());
    }

    /** 建一个测试资产。 */
    private Asset assessmentAsset(String name) {
        return assetService.register(ORG_PAY, name, "SYSTEM", "owner",
                com.mandao.grc.modules.asset.AssetClassification.INTERNAL,
                false, false, false, false, "HIGH", "c");
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
