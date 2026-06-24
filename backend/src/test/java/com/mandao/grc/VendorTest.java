package com.mandao.grc;

import com.mandao.grc.common.isolation.IsolationContext;
import com.mandao.grc.modules.assessment.RiskLevel;
import com.mandao.grc.modules.audit.ChainVerifyResult;
import com.mandao.grc.modules.audit.HashChainService;
import com.mandao.grc.modules.vendor.VendorService;
import com.mandao.grc.modules.vendor.VendorStatus;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第三方供应商集成测试（真实 PG + 切面 + RLS）。验证：
 *  1) 准入门控红线：未评估不得启用；评估后可启用；
 *  2) 监测状态机：暂停→恢复→终止，非法流转被拒；
 *  3) 组织隔离：org12 供应商，org13 看不到；
 *  4) 留痕：登记/评估/启用后哈希链校验通过且计数正确。
 *
 * 设计依据：需求 M·第三方供应商（准入/评估/监测）、D2-5。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "grc.scheduler.enabled=false")
@Testcontainers
class VendorTest {

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
    private VendorService vendorService;

    @Autowired
    private HashChainService hashChainService;

    private static final long ORG_PAY = 12L;
    private static final long ORG_CF = 13L;

    @BeforeEach
    void clean() throws Exception {
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement()) {
            s.executeUpdate("TRUNCATE vendor_assessment, vendor, operation_log RESTART IDENTITY CASCADE");
        }
    }

    @AfterEach
    void clearContext() {
        IsolationContext.clear();
    }

    @Test
    void 准入门控_未评估不得启用_评估后可启用并可监测() {
        Long vid = asOrg(ORG_PAY, () -> vendorService.register(ORG_PAY, "V-CLOUD", "某云服务商",
                "云服务", "ops@x.com", "关键", "c").getId());

        // 红线：未评估直接启用被拒
        assertThrows(IllegalStateException.class,
                () -> runAsOrg(ORG_PAY, () -> vendorService.activate(vid, "admin")));

        // 评估（高风险）→ 回写风险等级
        assertEquals(RiskLevel.HIGH,
                asOrg(ORG_PAY, () -> vendorService.assess(vid, RiskLevel.HIGH, 62, "数据出境风险偏高", "assessor").getRiskLevel()));

        // 评估后可启用
        assertEquals(VendorStatus.ACTIVE, asOrg(ORG_PAY, () -> vendorService.activate(vid, "admin").getStatus()));

        // 监测：暂停 → 恢复 → 终止
        assertEquals(VendorStatus.SUSPENDED, asOrg(ORG_PAY, () -> vendorService.suspend(vid, "SLA 异常", "ops").getStatus()));
        assertEquals(VendorStatus.ACTIVE, asOrg(ORG_PAY, () -> vendorService.reactivate(vid, "ops").getStatus()));
        assertEquals(VendorStatus.TERMINATED, asOrg(ORG_PAY, () -> vendorService.terminate(vid, "合同到期", "admin").getStatus()));
        // 终止后不可再暂停
        assertThrows(IllegalStateException.class,
                () -> runAsOrg(ORG_PAY, () -> vendorService.suspend(vid, "x", "ops")));
    }

    @Test
    void 组织隔离_org12供应商org13看不到() {
        asOrg(ORG_PAY, () -> vendorService.register(ORG_PAY, "V-X", "仅支付可见", "外包", null, "一般", "c"));
        assertEquals(1, asOrg(ORG_PAY, () -> vendorService.list()).size(), "org12 应看到自己的 1 个供应商");
        assertTrue(asOrg(ORG_CF, () -> vendorService.list()).isEmpty(), "org13 不应看到 org12 的供应商");
    }

    @Test
    void 留痕_登记评估启用共3条() {
        Long vid = asOrg(ORG_PAY, () -> vendorService.register(ORG_PAY, "V-H", "H", "云", null, "重要", "c").getId()); // REGISTER
        asOrg(ORG_PAY, () -> vendorService.assess(vid, RiskLevel.LOW, 88, "良好", "a"));                                // ASSESS
        asOrg(ORG_PAY, () -> vendorService.activate(vid, "admin"));                                                    // ACTIVATE

        ChainVerifyResult r = asOrg(ORG_PAY, () -> hashChainService.verify(ORG_PAY));
        assertTrue(r.valid(), "留痕后链应校验通过");
        assertEquals(3, r.count(), "应有 3 条供应商留痕");
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
