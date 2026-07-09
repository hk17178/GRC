package com.mandao.grc;

import com.mandao.grc.common.isolation.IsolationContext;
import com.mandao.grc.modules.aml.AmlService;
import com.mandao.grc.modules.aml.StrReport;
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

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 反洗钱 AML 集成测试（真实 PG + 切面 + RLS）。验证：
 *  1) 名单管理 + 筛查：登记名单，按名称子串/证件号精确筛中；停用后不再命中；
 *  2) STR 生命周期：DRAFT→SUBMITTED→REPORTED→CLOSED；非法流转/无回执号被拒；
 *  3) 组织隔离：org12 名单/STR，org13 看不到，且 org13 筛查不命中 org12 名单；
 *  4) 留痕：名单登记 + STR 全流程哈希链校验通过。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "grc.scheduler.enabled=false")
@Testcontainers
class AmlTest {

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
    private AmlService amlService;
    @Autowired
    private HashChainService hashChainService;

    private static final long ORG_PAY = 12L;
    private static final long ORG_CF = 13L;

    @BeforeEach
    void clean() throws Exception {
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement()) {
            s.executeUpdate("TRUNCATE aml_watchlist, str_report, operation_log RESTART IDENTITY CASCADE");
        }
    }

    @AfterEach
    void clearContext() {
        IsolationContext.clear();
    }

    @Test
    void 名单筛查_按名称与证件号命中_停用后不命中() {
        asOrg(ORG_PAY, () -> amlService.addWatchEntry(ORG_PAY, "SANCTION", "张三丰", "110101199001011234",
                "中国", "公安部", "涉恐名单", "c"));
        Long pepId = asOrg(ORG_PAY, () -> amlService.addWatchEntry(ORG_PAY, "PEP", "李四光", null,
                "中国", "内部", "政治敏感人物", "c").getId());

        // 名称子串命中
        assertEquals(1, asOrg(ORG_PAY, () -> amlService.screen("张三", null)).size(), "名称子串应命中张三丰");
        // 证件号精确命中
        List<AmlService.ScreenHit> byId = asOrg(ORG_PAY, () -> amlService.screen(null, "110101199001011234"));
        assertEquals(1, byId.size());
        assertEquals("ID", byId.get(0).matchBy());
        // 不匹配
        assertTrue(asOrg(ORG_PAY, () -> amlService.screen("王五", null)).isEmpty());

        // 停用 PEP 后，按其名筛不再命中
        asOrg(ORG_PAY, () -> amlService.retireWatchEntry(pepId, "c"));
        assertTrue(asOrg(ORG_PAY, () -> amlService.screen("李四光", null)).isEmpty(), "停用后不参与筛查");

        // 空条件筛查被拒
        assertThrows(IllegalArgumentException.class, () -> runAsOrg(ORG_PAY, () -> amlService.screen(null, null)));
    }

    @Test
    void str生命周期_登记提交报送了结_非法流转拒() {
        Long id = asOrg(ORG_PAY, () -> amlService.createStr(ORG_PAY, "某贸易公司", new BigDecimal("500000"),
                "HIGH", "短期内大额分拆转入转出", LocalDate.now().minusDays(2), "c").getId());

        // DRAFT 不能直接报送
        assertThrows(IllegalStateException.class, () -> runAsOrg(ORG_PAY, () ->
                amlService.reportStr(id, "人行反洗钱监测中心", "R2026-001", null, "c")));

        assertEquals("SUBMITTED", asOrg(ORG_PAY, () -> amlService.submitStr(id, "c")).getStatus());
        // 报送须回执号
        assertThrows(IllegalArgumentException.class, () -> runAsOrg(ORG_PAY, () ->
                amlService.reportStr(id, "人行监测中心", "  ", null, "c")));
        StrReport reported = asOrg(ORG_PAY, () -> amlService.reportStr(id, "人行反洗钱监测中心", "R2026-001", null, "c"));
        assertEquals("REPORTED", reported.getStatus());
        assertEquals("R2026-001", reported.getReportNo());
        assertTrue(reported.getReportedDate() != null, "报送日应回填");

        assertEquals("CLOSED", asOrg(ORG_PAY, () -> amlService.closeStr(id, "c")).getStatus());
        // 终态不可再提交
        assertThrows(IllegalStateException.class, () -> runAsOrg(ORG_PAY, () -> amlService.submitStr(id, "c")));
    }

    @Test
    void 组织隔离_org13不见org12名单与STR_筛查不跨域() {
        asOrg(ORG_PAY, () -> amlService.addWatchEntry(ORG_PAY, "SANCTION", "仅支付制裁对象", "PAYID001",
                "中国", "内部", "x", "c"));
        asOrg(ORG_PAY, () -> amlService.createStr(ORG_PAY, "支付可疑主体", new BigDecimal("1"), "LOW", "x", null, "c"));

        assertEquals(1, asOrg(ORG_PAY, () -> amlService.listWatchlist()).size());
        assertTrue(asOrg(ORG_CF, () -> amlService.listWatchlist()).isEmpty(), "org13 不应看到 org12 名单");
        assertTrue(asOrg(ORG_CF, () -> amlService.listStr()).isEmpty(), "org13 不应看到 org12 STR");
        // 跨域筛查不命中
        assertTrue(asOrg(ORG_CF, () -> amlService.screen("仅支付制裁对象", "PAYID001")).isEmpty(),
                "org13 筛查不得命中 org12 名单（RLS）");
    }

    @Test
    void 留痕_名单与STR全流程链校验() {
        asOrg(ORG_PAY, () -> amlService.addWatchEntry(ORG_PAY, "INTERNAL", "黑名单甲", null, null, "内部", "x", "c")); // AML_WATCH_ADD
        Long id = asOrg(ORG_PAY, () -> amlService.createStr(ORG_PAY, "主体", new BigDecimal("100"), "MID", "x", null, "c").getId()); // STR_CREATE
        asOrg(ORG_PAY, () -> amlService.submitStr(id, "c"));   // STR_SUBMIT
        asOrg(ORG_PAY, () -> amlService.reportStr(id, "监测中心", "R1", null, "c"));   // STR_REPORT
        asOrg(ORG_PAY, () -> amlService.closeStr(id, "c"));    // STR_CLOSE

        ChainVerifyResult r = asOrg(ORG_PAY, () -> hashChainService.verify(ORG_PAY));
        assertTrue(r.valid(), "留痕链应校验通过");
        assertEquals(5, r.count(), "名单登记 + STR 登记/提交/报送/了结 = 5 条");
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

    private void runAsOrg(long orgId, Callable<?> action) throws Exception {
        IsolationContext.set(List.of(orgId));
        try {
            action.call();
        } finally {
            IsolationContext.clear();
        }
    }
}
