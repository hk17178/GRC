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

    private static final long ORG_PAY = 12L;

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
