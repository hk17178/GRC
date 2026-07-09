package com.mandao.grc;

import com.mandao.grc.common.isolation.IsolationContext;
import com.mandao.grc.modules.asset.AssetClassification;
import com.mandao.grc.modules.asset.AssetService;
import com.mandao.grc.modules.assessment.RiskLevel;
import com.mandao.grc.modules.atv.AtvService;
import com.mandao.grc.modules.atv.RiskScenario;
import com.mandao.grc.modules.atv.Threat;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A-T-V 风险场景集成测试（真实 PG + 切面 + RLS）。验证：
 *  1) 风险矩阵分档：可能性×影响 → 平台五级（含边界）；
 *  2) 登记场景派生固有等级、重评重算；校验可能性/影响越界、资产不可见被拒（桥接 M6）；
 *  3) 组织隔离：org12 威胁，org13 看不到；
 *  4) 留痕：登记资产+威胁+脆弱+场景 = 4 条，链校验通过。
 *
 * 设计依据：D1-2/D1-3（A-T-V、风险矩阵、五级）、D1-7、D2-5。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "grc.scheduler.enabled=false")
@Testcontainers
class AtvRiskScenarioTest {

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
    private HashChainService hashChainService;

    private static final long ORG_PAY = 12L;
    private static final long ORG_CF = 13L;

    @BeforeEach
    void clean() throws Exception {
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement()) {
            s.executeUpdate("TRUNCATE risk_scenario, threat, vulnerability, operation_log RESTART IDENTITY CASCADE");
            // 资产由各用例自建；清除用例新建资产（保留 V9 种子），避免跨用例累积。
            s.executeUpdate("DELETE FROM asset WHERE id >= 1000");
        }
    }

    @AfterEach
    void clearContext() {
        IsolationContext.clear();
    }

    @Test
    void 风险矩阵分档_可能性乘影响映射五级() {
        assertEquals(RiskLevel.VERY_LOW, RiskScenario.deriveLevel(1, 1));   // 1
        assertEquals(RiskLevel.VERY_LOW, RiskScenario.deriveLevel(2, 2));   // 4
        assertEquals(RiskLevel.LOW, RiskScenario.deriveLevel(1, 5));        // 5
        assertEquals(RiskLevel.LOW, RiskScenario.deriveLevel(2, 4));        // 8
        assertEquals(RiskLevel.MID, RiskScenario.deriveLevel(3, 3));        // 9
        assertEquals(RiskLevel.MID, RiskScenario.deriveLevel(3, 4));        // 12
        assertEquals(RiskLevel.HIGH, RiskScenario.deriveLevel(4, 4));       // 16
        assertEquals(RiskLevel.VERY_HIGH, RiskScenario.deriveLevel(4, 5));  // 20
        assertEquals(RiskLevel.VERY_HIGH, RiskScenario.deriveLevel(5, 5));  // 25
    }

    @Test
    void 登记场景_派生固有等级并可重评() {
        Long assetId = asOrg(ORG_PAY, this::registerPayAsset);
        Long tId = asOrg(ORG_PAY, () -> atvService.createThreat(ORG_PAY, "T-INJ", "SQL注入", "应用攻击", "x", "c").getId());
        Long vId = asOrg(ORG_PAY, () -> atvService.createVulnerability(ORG_PAY, "V-INPUT", "输入校验缺失", "配置", "x", "c").getId());

        // 可能性 4 × 影响 5 = 20 → 极高
        RiskScenario sc = asOrg(ORG_PAY, () -> atvService.createScenario(assetId, tId, vId, 4, 5, "核心库可被注入", "c"));
        assertEquals(RiskLevel.VERY_HIGH, sc.getInherentLevel());

        // 重评 1 × 2 = 2 → 极低
        RiskScenario re = asOrg(ORG_PAY, () -> atvService.reassess(sc.getId(), 1, 2, "已加固"));
        assertEquals(RiskLevel.VERY_LOW, re.getInherentLevel());
    }

    @Test
    void 校验_越界与资产不可见被拒() {
        Long assetId = asOrg(ORG_PAY, this::registerPayAsset);
        Long tId = asOrg(ORG_PAY, () -> atvService.createThreat(ORG_PAY, "T1", "t", "c", "x", "c").getId());
        Long vId = asOrg(ORG_PAY, () -> atvService.createVulnerability(ORG_PAY, "V1", "v", "c", "x", "c").getId());

        // 可能性越界（6）
        assertThrows(IllegalArgumentException.class,
                () -> runAsOrg(ORG_PAY, () -> atvService.createScenario(assetId, tId, vId, 6, 3, "x", "c")));
        // 资产不可见（不存在 id）→ 桥接 M6 校验拒绝
        assertThrows(IllegalArgumentException.class,
                () -> runAsOrg(ORG_PAY, () -> atvService.createScenario(999999L, tId, vId, 3, 3, "x", "c")));
    }

    @Test
    void 组织隔离_org12威胁_org13看不到() {
        asOrg(ORG_PAY, () -> atvService.createThreat(ORG_PAY, "T-X", "仅支付可见", "c", "x", "c"));
        assertEquals(1, asOrg(ORG_PAY, () -> atvService.listThreats()).size(), "org12 应看到自己的 1 个威胁");
        assertTrue(asOrg(ORG_CF, () -> atvService.listThreats()).isEmpty(), "org13 不应看到 org12 的威胁");
    }

    @Test
    void 留痕_资产威胁脆弱场景共4条() {
        Long assetId = asOrg(ORG_PAY, this::registerPayAsset);                                                  // ASSET_REGISTER
        Long tId = asOrg(ORG_PAY, () -> atvService.createThreat(ORG_PAY, "T-H", "t", "c", "x", "c").getId());   // THREAT_CREATE
        Long vId = asOrg(ORG_PAY, () -> atvService.createVulnerability(ORG_PAY, "V-H", "v", "c", "x", "c").getId()); // VULN_CREATE
        asOrg(ORG_PAY, () -> atvService.createScenario(assetId, tId, vId, 3, 3, "x", "c"));                     // SCENARIO_CREATE

        ChainVerifyResult r = asOrg(ORG_PAY, () -> hashChainService.verify(ORG_PAY));
        assertTrue(r.valid(), "留痕后链应校验通过");
        assertEquals(4, r.count(), "应有 4 条留痕（资产+威胁+脆弱+场景）");
    }

    // ---------- B44：漏扫结果导入脆弱性库 ----------

    @Test
    void b44_漏扫导入_按code去重_脏条目跳过_留痕() {
        List<AtvService.ScanItem> batch1 = List.of(
                new AtvService.ScanItem("CVE-2024-1", "OpenSSL 心脏出血", "加密", "TLS 缓冲越界"),
                new AtvService.ScanItem("CVE-2024-2", "弱口令", "访问控制", "默认口令未改"),
                new AtvService.ScanItem("", "脏条目", "x", "缺编码"),                 // 脏：无 code → 跳过
                new AtvService.ScanItem("CVE-2024-3", "  ", "x", "缺名称"));           // 脏：无 name → 跳过
        AtvService.ScanImportResult r1 = asOrg(ORG_PAY, () -> atvService.importVulnScan(ORG_PAY, batch1, "scanner"));
        assertEquals(2, r1.imported(), "两条有效条目入库");
        assertEquals(2, r1.skipped(), "两条脏条目跳过");
        assertEquals(2, asOrg(ORG_PAY, () -> atvService.listVulnerabilities()).size());

        // 再次导入：重叠 code 去重（不覆盖），仅 1 条新
        List<AtvService.ScanItem> batch2 = List.of(
                new AtvService.ScanItem("CVE-2024-1", "改写尝试", "加密", "不应覆盖"),   // 已存在 → 跳过
                new AtvService.ScanItem("CVE-2024-9", "新漏洞", "补丁", "新增"));         // 新 → 入库
        AtvService.ScanImportResult r2 = asOrg(ORG_PAY, () -> atvService.importVulnScan(ORG_PAY, batch2, "scanner"));
        assertEquals(1, r2.imported());
        assertEquals(1, r2.skipped());
        assertEquals(3, asOrg(ORG_PAY, () -> atvService.listVulnerabilities()).size(), "共 3 条（去重后无重复）");

        // 留痕：两批各一条 VULN_SCAN_IMPORT（有新增才留痕）
        ChainVerifyResult r = asOrg(ORG_PAY, () -> hashChainService.verify(ORG_PAY));
        assertTrue(r.valid());
        assertEquals(2, r.count(), "两批导入各留痕一条");
    }

    @Test
    void b44_导入隔离_org13不见org12导入的脆弱性() {
        asOrg(ORG_PAY, () -> atvService.importVulnScan(ORG_PAY,
                List.of(new AtvService.ScanItem("CVE-ISO", "仅支付", "配置", "x")), "scanner"));
        assertEquals(1, asOrg(ORG_PAY, () -> atvService.listVulnerabilities()).size());
        assertTrue(asOrg(ORG_CF, () -> atvService.listVulnerabilities()).isEmpty(),
                "org13 不应看到 org12 导入的脆弱性");
    }

    // ---------- 测试辅助 ----------

    /** 在 ORG_PAY 登记一个资产，返回 id。 */
    private Long registerPayAsset() {
        return assetService.register(ORG_PAY, "核心支付数据库", "DATABASE", "dba",
                AssetClassification.INTERNAL, true, false, true, true, "HIGH", "creator").getId();
    }

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
