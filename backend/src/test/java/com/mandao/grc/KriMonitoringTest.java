package com.mandao.grc;

import com.mandao.grc.common.isolation.IsolationContext;
import com.mandao.grc.modules.audit.ChainVerifyResult;
import com.mandao.grc.modules.audit.HashChainService;
import com.mandao.grc.modules.kri.Kri;
import com.mandao.grc.modules.kri.KriDirection;
import com.mandao.grc.modules.kri.KriMeasurement;
import com.mandao.grc.modules.kri.KriService;
import com.mandao.grc.modules.kri.KriStatus;
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
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * KRI 监控集成测试（真实 PG + 应用切面 + RLS）。验证：
 *  1) UPPER_BAD（越高越坏）双阈值评定 NORMAL/WARNING/CRITICAL，并回写指标最近值/状态；
 *  2) LOWER_BAD（越低越坏）方向评定正确；
 *  3) 组织隔离：org12 的 KRI，在 org13 上下文中看不到；
 *  4) 留痕：定义 + 测量后哈希链 verify 通过且计数正确；
 *  5) 测量历史按最新在前返回。
 *
 * 设计依据：D1-2（KRI/阈值/监测）、D1-7（风险评估·KRI 监控）、D2-5。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "grc.scheduler.enabled=false")
@Testcontainers
class KriMonitoringTest {

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
    private KriService kriService;

    @Autowired
    private HashChainService hashChainService;

    private static final long ORG_PAY = 12L;
    private static final long ORG_CF = 13L;

    @BeforeEach
    void clean() throws Exception {
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement()) {
            s.executeUpdate("TRUNCATE kri_measurement, kri, operation_log RESTART IDENTITY CASCADE");
        }
    }

    @AfterEach
    void clearContext() {
        IsolationContext.clear();
    }

    @Test
    void 越高越坏_双阈值评定并回写最近态() {
        // 高危漏洞数：预警 5、严重 10，越高越坏
        Long id = asOrg(ORG_PAY, () -> kriService.create(ORG_PAY, "KRI-VULN", "高危漏洞数", "个",
                KriDirection.UPPER_BAD, new BigDecimal("5"), new BigDecimal("10"), "secteam", "creator").getId());

        assertEquals(KriStatus.NORMAL, asOrg(ORG_PAY, () -> kriService.record(id, new BigDecimal("3"), null, "m").getStatus()));
        assertEquals(KriStatus.WARNING, asOrg(ORG_PAY, () -> kriService.record(id, new BigDecimal("7"), null, "m").getStatus()));
        assertEquals(KriStatus.CRITICAL, asOrg(ORG_PAY, () -> kriService.record(id, new BigDecimal("12"), null, "m").getStatus()));

        // 回写：最近值 12、最近状态 CRITICAL
        Kri kri = asOrg(ORG_PAY, () -> kriService.get(id));
        assertEquals(0, kri.getCurrentValue().compareTo(new BigDecimal("12")));
        assertEquals(KriStatus.CRITICAL, kri.getCurrentStatus());
    }

    @Test
    void 越低越坏_方向评定正确() {
        // 补丁覆盖率：预警 90、严重 80，越低越坏
        Long id = asOrg(ORG_PAY, () -> kriService.create(ORG_PAY, "KRI-PATCH", "补丁覆盖率", "%",
                KriDirection.LOWER_BAD, new BigDecimal("90"), new BigDecimal("80"), "ops", "creator").getId());

        assertEquals(KriStatus.NORMAL, asOrg(ORG_PAY, () -> kriService.record(id, new BigDecimal("95"), null, "m").getStatus()));
        assertEquals(KriStatus.WARNING, asOrg(ORG_PAY, () -> kriService.record(id, new BigDecimal("85"), null, "m").getStatus()));
        assertEquals(KriStatus.CRITICAL, asOrg(ORG_PAY, () -> kriService.record(id, new BigDecimal("70"), null, "m").getStatus()));
    }

    @Test
    void 组织隔离_org12的KRI_org13看不到() {
        asOrg(ORG_PAY, () -> kriService.create(ORG_PAY, "KRI-X", "仅支付可见", "个",
                KriDirection.UPPER_BAD, new BigDecimal("1"), new BigDecimal("2"), null, "creator"));

        assertEquals(1, asOrg(ORG_PAY, () -> kriService.list()).size(), "org12 应看到自己的 1 个 KRI");
        assertTrue(asOrg(ORG_CF, () -> kriService.list()).isEmpty(), "org13 不应看到 org12 的 KRI");
    }

    @Test
    void 测量历史最新在前_且留痕计数正确() {
        Long id = asOrg(ORG_PAY, () -> kriService.create(ORG_PAY, "KRI-H", "趋势", "个",
                KriDirection.UPPER_BAD, new BigDecimal("5"), new BigDecimal("10"), null, "creator").getId()); // KRI_CREATE
        asOrg(ORG_PAY, () -> kriService.record(id, new BigDecimal("1"), "首测", "m"));   // KRI_MEASURE
        asOrg(ORG_PAY, () -> kriService.record(id, new BigDecimal("8"), "二测", "m"));   // KRI_MEASURE

        List<KriMeasurement> history = asOrg(ORG_PAY, () -> kriService.listMeasurements(id));
        assertEquals(2, history.size());
        assertEquals(0, history.get(0).getValue().compareTo(new BigDecimal("8")), "最新测量应在最前");

        ChainVerifyResult r = asOrg(ORG_PAY, () -> hashChainService.verify(ORG_PAY));
        assertTrue(r.valid(), "留痕后链应校验通过");
        assertEquals(3, r.count(), "应有 3 条留痕（定义 + 两次测量）");
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
