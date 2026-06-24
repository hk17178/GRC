package com.mandao.grc;

import com.mandao.grc.common.isolation.IsolationContext;
import com.mandao.grc.modules.audit.ChainVerifyResult;
import com.mandao.grc.modules.audit.HashChainService;
import com.mandao.grc.modules.obligation.ObligationService;
import com.mandao.grc.modules.obligation.ObligationStatus;
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
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 合规清单集成测试（真实 PG + 切面 + RLS）。验证：
 *  1) 落实闭环红线：无证据不得标记已落实；有证据可落实；
 *  2) 状态机：不合规 → 整改(start) → 落实；非法流转被拒；
 *  3) 组织隔离：org12 义务，org13 看不到；
 *  4) 留痕：登记/落实流转哈希链校验通过且计数正确。
 *
 * 设计依据：需求 M·合规清单、D2-5。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "grc.scheduler.enabled=false")
@Testcontainers
class ObligationTest {

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
    private ObligationService obligationService;

    @Autowired
    private HashChainService hashChainService;

    private static final long ORG_PAY = 12L;
    private static final long ORG_CF = 13L;
    private static final LocalDate TODAY = LocalDate.of(2026, 6, 25);

    @BeforeEach
    void clean() throws Exception {
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement()) {
            s.executeUpdate("TRUNCATE obligation, operation_log RESTART IDENTITY CASCADE");
        }
    }

    @AfterEach
    void clearContext() {
        IsolationContext.clear();
    }

    @Test
    void 落实闭环_无证据不得落实_有证据可落实() {
        Long id = asOrg(ORG_PAY, () -> obligationService.create(ORG_PAY, "OBL-AML-1", "可疑交易报告",
                "PBOC-AML-2026", "反洗钱", "按月报送可疑交易", "合规部", TODAY.plusDays(30), "c").getId());

        assertEquals(ObligationStatus.IN_PROGRESS, asOrg(ORG_PAY, () -> obligationService.start(id, "owner").getStatus()));

        // 红线：无证据标记已落实被拒
        assertThrows(IllegalArgumentException.class,
                () -> runAsOrg(ORG_PAY, () -> obligationService.fulfill(id, "  ", "owner")));

        // 有证据可落实
        assertEquals(ObligationStatus.FULFILLED,
                asOrg(ORG_PAY, () -> obligationService.fulfill(id, "已上线可疑交易监测系统，月报存档 #2026-06", "owner").getStatus()));
    }

    @Test
    void 状态机_不合规后可整改再落实() {
        Long id = asOrg(ORG_PAY, () -> obligationService.create(ORG_PAY, "OBL-X", "X", "src", "c", "req", "部门", TODAY, "c").getId());
        // 标记不合规
        assertEquals(ObligationStatus.NON_COMPLIANT,
                asOrg(ORG_PAY, () -> obligationService.markNonCompliant(id, "未按期落实", "auditor").getStatus()));
        // 整改：再开始 → 落实
        assertEquals(ObligationStatus.IN_PROGRESS, asOrg(ORG_PAY, () -> obligationService.start(id, "owner").getStatus()));
        assertEquals(ObligationStatus.FULFILLED, asOrg(ORG_PAY, () -> obligationService.fulfill(id, "整改完成证据", "owner").getStatus()));
        // 已落实不可再标记不合规
        assertThrows(IllegalStateException.class,
                () -> runAsOrg(ORG_PAY, () -> obligationService.markNonCompliant(id, "x", "a")));
    }

    @Test
    void 组织隔离_org12义务org13看不到() {
        asOrg(ORG_PAY, () -> obligationService.create(ORG_PAY, "OBL-ISO", "仅支付可见", "s", "c", "r", "d", null, "c"));
        assertEquals(1, asOrg(ORG_PAY, () -> obligationService.list()).size(), "org12 应看到自己的 1 条义务");
        assertTrue(asOrg(ORG_CF, () -> obligationService.list()).isEmpty(), "org13 不应看到 org12 的义务");
    }

    @Test
    void 留痕_登记开始落实共3条() {
        Long id = asOrg(ORG_PAY, () -> obligationService.create(ORG_PAY, "OBL-H", "H", "s", "c", "r", "d", null, "c").getId()); // CREATE
        asOrg(ORG_PAY, () -> obligationService.start(id, "owner"));                                                              // START
        asOrg(ORG_PAY, () -> obligationService.fulfill(id, "证据", "owner"));                                                    // FULFILL

        ChainVerifyResult r = asOrg(ORG_PAY, () -> hashChainService.verify(ORG_PAY));
        assertTrue(r.valid(), "留痕后链应校验通过");
        assertEquals(3, r.count(), "应有 3 条合规义务留痕");
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
