package com.mandao.grc;

import com.mandao.grc.common.isolation.IsolationContext;
import com.mandao.grc.modules.audit.ChainVerifyResult;
import com.mandao.grc.modules.audit.HashChainService;
import com.mandao.grc.modules.regulation.ChangeType;
import com.mandao.grc.modules.regulation.ImpactStatus;
import com.mandao.grc.modules.regulation.RegulationChange;
import com.mandao.grc.modules.regulation.RegulationService;
import com.mandao.grc.modules.regulation.RegulationStatus;
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
 * 法规跟踪集成测试（真实 PG + 切面 + RLS）。验证：
 *  1) 法规状态机 + 变更动态 + 影响评估闭环（PENDING→ASSESSED，重复评估被拒）；
 *  2) 终态法规不可再变状态；
 *  3) 组织隔离：org12 法规，org13 看不到；
 *  4) 留痕：登记/变更/评估后哈希链校验通过且计数正确。
 *
 * 设计依据：需求 M·法规跟踪、D2-5。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "grc.scheduler.enabled=false")
@Testcontainers
class RegulationTrackingTest {

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
    private RegulationService regulationService;

    @Autowired
    private HashChainService hashChainService;

    private static final long ORG_PAY = 12L;
    private static final long ORG_CF = 13L;
    private static final LocalDate TODAY = LocalDate.of(2026, 6, 24);

    @BeforeEach
    void clean() throws Exception {
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement()) {
            s.executeUpdate("TRUNCATE regulation_change, regulation, operation_log RESTART IDENTITY CASCADE");
        }
    }

    @AfterEach
    void clearContext() {
        IsolationContext.clear();
    }

    @Test
    void 法规跟踪_变更影响评估闭环() {
        Long rid = asOrg(ORG_PAY, () -> regulationService.create(ORG_PAY, "PBOC-AML-2026", "反洗钱管理办法",
                "人民银行", "反洗钱", TODAY.plusDays(60), "摘要", "c").getId());

        // 生效
        assertEquals(RegulationStatus.EFFECTIVE,
                asOrg(ORG_PAY, () -> regulationService.updateStatus(rid, RegulationStatus.EFFECTIVE, "c").getStatus()));

        // 登记一条修订变更（PENDING）
        Long cid = asOrg(ORG_PAY, () -> regulationService.recordChange(rid, ChangeType.AMENDED,
                TODAY, "新增受益所有人识别要求", "c").getId());

        // 完成影响评估 → ASSESSED
        assertEquals(ImpactStatus.ASSESSED,
                asOrg(ORG_PAY, () -> regulationService.assessImpact(cid, "涉及 KYC 制度与客户身份识别控制项",
                        "更新制度 POL-KYC、新增控制项", "compliance").getImpactStatus()));

        // 重复评估被拒
        assertThrows(IllegalStateException.class,
                () -> runAsOrg(ORG_PAY, () -> regulationService.assessImpact(cid, "x", "y", "c")));

        List<RegulationChange> changes = asOrg(ORG_PAY, () -> regulationService.listChanges(rid));
        assertEquals(1, changes.size());
    }

    @Test
    void 终态法规不可再变状态() {
        Long rid = asOrg(ORG_PAY, () -> regulationService.create(ORG_PAY, "R-X", "X", "i", "c", null, null, "c").getId());
        asOrg(ORG_PAY, () -> regulationService.updateStatus(rid, RegulationStatus.ABOLISHED, "c"));
        // 已废止(终态)不可再变
        assertThrows(IllegalStateException.class,
                () -> runAsOrg(ORG_PAY, () -> regulationService.updateStatus(rid, RegulationStatus.EFFECTIVE, "c")));
    }

    @Test
    void 组织隔离_org12法规org13看不到() {
        asOrg(ORG_PAY, () -> regulationService.create(ORG_PAY, "R-ISO", "仅支付可见", "i", "c", null, null, "c"));
        assertEquals(1, asOrg(ORG_PAY, () -> regulationService.list()).size(), "org12 应看到自己的 1 条法规");
        assertTrue(asOrg(ORG_CF, () -> regulationService.list()).isEmpty(), "org13 不应看到 org12 的法规");
    }

    @Test
    void 留痕_登记变更评估共4条() {
        Long rid = asOrg(ORG_PAY, () -> regulationService.create(ORG_PAY, "R-H", "H", "i", "c", null, null, "c").getId()); // CREATE
        asOrg(ORG_PAY, () -> regulationService.updateStatus(rid, RegulationStatus.EFFECTIVE, "c"));                          // STATUS
        Long cid = asOrg(ORG_PAY, () -> regulationService.recordChange(rid, ChangeType.AMENDED, TODAY, "x", "c").getId());  // CHANGE
        asOrg(ORG_PAY, () -> regulationService.assessImpact(cid, "scope", "note", "c"));                                    // IMPACT_ASSESS

        ChainVerifyResult r = asOrg(ORG_PAY, () -> hashChainService.verify(ORG_PAY));
        assertTrue(r.valid(), "留痕后链应校验通过");
        assertEquals(4, r.count(), "应有 4 条法规跟踪留痕");
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
