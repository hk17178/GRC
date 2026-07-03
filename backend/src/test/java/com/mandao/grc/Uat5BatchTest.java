package com.mandao.grc;

import com.mandao.grc.common.isolation.IsolationContext;
import com.mandao.grc.modules.asset.Asset;
import com.mandao.grc.modules.asset.AssetClassification;
import com.mandao.grc.modules.asset.AssetService;
import com.mandao.grc.modules.assessment.Assessment;
import com.mandao.grc.modules.assessment.AssessmentService;
import com.mandao.grc.modules.assessment.AssessmentStatus;
import com.mandao.grc.modules.assessment.RiskLevel;
import com.mandao.grc.modules.assessment.TemplateService;
import com.mandao.grc.modules.assessment.form.AssessmentFormService;
import com.mandao.grc.modules.atv.AtvService;
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
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UAT 五轮批次集成测试。验证：
 *  1) 删除口径：DRAFT 物理删除（级联清理发现/填写/范围资产）；非草稿删除被拒；
 *     IN_PROGRESS 可作废（CANCELLED 终态）；COMPLETED 不可作废；
 *  2) 方向 A 预填：范围资产 + A-T-V 场景在首次打开表单时自动预填 资产清单/威胁脆弱性清单/风险清单。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "grc.scheduler.enabled=false")
@Testcontainers
class Uat5BatchTest {

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
    private AssessmentFormService formService;
    @Autowired
    private TemplateService templateService;
    @Autowired
    private AssetService assetService;
    @Autowired
    private AtvService atvService;

    private static final long ORG_PAY = 12L;

    @BeforeEach
    void clean() throws Exception {
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement()) {
            s.executeUpdate("TRUNCATE risk_treatment, risk_finding, assessment_answer, assessment_asset, "
                    + "assessment_doc, risk_scenario, threat, vulnerability, asset CASCADE");
            s.executeUpdate("DELETE FROM assessment WHERE org_id = 12");
        }
    }

    @AfterEach
    void clearContext() {
        IsolationContext.clear();
    }

    @Test
    void 删除口径_草稿级联删_非草稿作废_完成冻结() {
        Assessment draft = asOrg(ORG_PAY, () -> assessmentService.create(ORG_PAY, "草稿待删", "u", "2026", "c"));
        Asset asset = asOrg(ORG_PAY, () -> assetService.register(ORG_PAY, "删测资产", "SYSTEM", "o",
                AssetClassification.INTERNAL, false, false, false, false, "HIGH", "c"));
        asOrg(ORG_PAY, () -> assessmentService.addScopeAsset(draft.getId(), asset.getId(), "c"));

        // 草稿删除（级联范围资产）
        asOrg(ORG_PAY, () -> { assessmentService.deleteDraft(draft.getId(), "c"); return null; });
        assertThrows(IllegalArgumentException.class,
                () -> asOrg(ORG_PAY, () -> assessmentService.get(draft.getId())), "删除后应不可见");

        // 非草稿删除被拒 → 作废
        Assessment running = asOrg(ORG_PAY, () -> assessmentService.create(ORG_PAY, "进行中", "u", "2026", "c"));
        asOrg(ORG_PAY, () -> assessmentService.start(running.getId(), "c"));
        assertThrows(IllegalStateException.class, () -> asOrg(ORG_PAY, () -> {
            assessmentService.deleteDraft(running.getId(), "c");
            return null;
        }), "非草稿不可物理删除");
        Assessment cancelled = asOrg(ORG_PAY, () -> assessmentService.cancel(running.getId(), "测试作废", "c"));
        assertEquals(AssessmentStatus.CANCELLED, cancelled.getStatus());

        // COMPLETED 不可作废
        Assessment done = asOrg(ORG_PAY, () -> assessmentService.create(ORG_PAY, "完成件", "u", "2026", "c"));
        asOrg(ORG_PAY, () -> assessmentService.start(done.getId(), "c"));
        asOrg(ORG_PAY, () -> assessmentService.submitForReview(done.getId(), "c"));
        asOrg(ORG_PAY, () -> assessmentService.complete(done.getId(), "c"));
        assertThrows(IllegalStateException.class, () -> asOrg(ORG_PAY, () ->
                assessmentService.cancel(done.getId(), null, "c")), "定稿档案不可作废");
    }

    @Test
    @SuppressWarnings("unchecked")
    void 方向A预填_范围资产与ATV场景自动带入表单明细表() {
        // 内置模板（启动引导已预装标准表单）
        var tpl = asOrg(1L, () -> templateService.list().stream()
                .filter(t -> "TPL-MLPS".equals(t.getCode())).findFirst().orElseThrow());

        Asset asset = asOrg(ORG_PAY, () -> assetService.register(ORG_PAY, "核心网关", "SYSTEM", "o",
                AssetClassification.SENSITIVE, true, false, true, false, "CRITICAL", "c"));
        Threat t = asOrg(ORG_PAY, () -> atvService.createThreat(ORG_PAY, "T1", "外部攻击", "网络", null, "c"));
        Vulnerability v = asOrg(ORG_PAY, () -> atvService.createVulnerability(ORG_PAY, "V1", "弱口令", "配置", null, "c"));
        var sc = asOrg(ORG_PAY, () -> atvService.createScenario(asset.getId(), t.getId(), v.getId(), 4, 4, "口令策略缺失", "c"));

        // 集团管理员视角（可见域含 org1 模板与 org12 数据）创建评估并纳入范围资产
        Assessment a = asOrgs(List.of(1L, 12L), () ->
                assessmentService.create(ORG_PAY, "预填评估", "u", "2026", tpl.getId(), "c"));
        asOrg(ORG_PAY, () -> assessmentService.addScopeAsset(a.getId(), asset.getId(), "c"));

        var view = asOrgs(List.of(1L, 12L), () -> formService.getAssessmentForm(a.getId()));
        Map<String, Object> answers = (Map<String, Object>) view.answers();
        var assetRows = (List<Map<String, Object>>) answers.get("资产清单");
        assertEquals(1, assetRows.size(), "范围资产应预填资产清单");
        assertEquals("核心网关", assetRows.get(0).get("资产名称"));
        assertEquals("VERY_HIGH", assetRows.get(0).get("重要程度"), "CRITICAL 应映射五级 VERY_HIGH");

        var tvRows = (List<Map<String, Object>>) answers.get("威胁脆弱性清单");
        assertEquals("外部攻击", tvRows.get(0).get("威胁"));
        var riskRows = (List<Map<String, Object>>) answers.get("风险清单");
        assertEquals(1, riskRows.size());
        assertTrue(String.valueOf(riskRows.get(0).get("风险描述")).contains("核心网关"), "风险描述应含资产名");
        assertEquals(sc.getInherentLevel().name(), riskRows.get(0).get("固有等级"), "场景派生固有等级应带入");
    }

    private <T> T asOrg(long orgId, Supplier<T> action) {
        return asOrgs(List.of(orgId), action);
    }

    private <T> T asOrgs(List<Long> orgIds, Supplier<T> action) {
        IsolationContext.set(orgIds);
        try {
            return action.get();
        } finally {
            IsolationContext.clear();
        }
    }
}
