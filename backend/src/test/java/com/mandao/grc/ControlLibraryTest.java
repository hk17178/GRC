package com.mandao.grc;

import com.mandao.grc.common.isolation.IsolationContext;
import com.mandao.grc.modules.audit.ChainVerifyResult;
import com.mandao.grc.modules.audit.HashChainService;
import com.mandao.grc.modules.control.Control;
import com.mandao.grc.modules.control.ControlFramework;
import com.mandao.grc.modules.control.ControlFrameworkRef;
import com.mandao.grc.modules.control.ControlService;
import com.mandao.grc.modules.control.ControlStatus;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 统一控件库集成测试（真实 PG + 应用切面 + RLS）。验证：
 *  1) 定义控制项 + 一控多框架映射（ISO/等保/PCI），同框架同条款判重；
 *  2) 停用后不可再加映射、不可重复停用（状态机）；
 *  3) 组织隔离：org12 的控制项，在 org13 上下文中看不到；
 *  4) 留痕：定义 + 映射 + 停用后哈希链 verify 通过且计数正确。
 *
 * 设计依据：D1-2（统一控件库/框架映射）、D1-7（风险评估·统一控件库）、D2-5。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "grc.scheduler.enabled=false")
@Testcontainers
class ControlLibraryTest {

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
    private ControlService controlService;

    @Autowired
    private HashChainService hashChainService;

    private static final long ORG_PAY = 12L;
    private static final long ORG_CF = 13L;

    @BeforeEach
    void clean() throws Exception {
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement()) {
            s.executeUpdate("TRUNCATE control_test, control_framework_ref, control_item, operation_log RESTART IDENTITY CASCADE");
        }
    }

    @AfterEach
    void clearContext() {
        IsolationContext.clear();
    }

    @Test
    void 一控多框架映射_并判重() {
        Long id = asOrg(ORG_PAY, () -> controlService.create(ORG_PAY, "CTL-ACL-001", "最小权限访问控制",
                "按职责最小授权", "访问控制", "secteam", "creator").getId());

        asOrg(ORG_PAY, () -> controlService.addMapping(id, ControlFramework.ISO27001, "A.9.2.3", "m"));
        asOrg(ORG_PAY, () -> controlService.addMapping(id, ControlFramework.MLPS, "8.1.4.2", "m"));
        asOrg(ORG_PAY, () -> controlService.addMapping(id, ControlFramework.PCI_DSS, "7.1.2", "m"));

        List<ControlFrameworkRef> refs = asOrg(ORG_PAY, () -> controlService.listMappings(id));
        assertEquals(3, refs.size(), "一个控制项应可映射到 3 个框架条款");

        // 同框架同条款判重
        assertThrows(IllegalStateException.class,
                () -> runAsOrg(ORG_PAY, () -> controlService.addMapping(id, ControlFramework.ISO27001, "A.9.2.3", "m")));
    }

    @Test
    void 停用后不可加映射且不可重复停用() {
        Long id = asOrg(ORG_PAY, () -> controlService.create(ORG_PAY, "CTL-X", "X", "x", "日志", null, "creator").getId());

        assertEquals(ControlStatus.RETIRED, asOrg(ORG_PAY, () -> controlService.retire(id, "admin").getStatus()));
        // 停用后不可加映射
        assertThrows(IllegalStateException.class,
                () -> runAsOrg(ORG_PAY, () -> controlService.addMapping(id, ControlFramework.PBOC, "X", "m")));
        // 不可重复停用
        assertThrows(IllegalStateException.class,
                () -> runAsOrg(ORG_PAY, () -> controlService.retire(id, "admin")));
    }

    @Test
    void 组织隔离_org12的控制项_org13看不到() {
        asOrg(ORG_PAY, () -> controlService.create(ORG_PAY, "CTL-ISO", "仅支付可见", "x", "加密", null, "creator"));

        assertEquals(1, asOrg(ORG_PAY, () -> controlService.list()).size(), "org12 应看到自己的 1 个控制项");
        assertTrue(asOrg(ORG_CF, () -> controlService.list()).isEmpty(), "org13 不应看到 org12 的控制项");
    }

    @Test
    void 留痕_定义映射停用后链校验通过且计数正确() {
        Long id = asOrg(ORG_PAY, () -> controlService.create(ORG_PAY, "CTL-H", "H", "h", "审计", null, "creator").getId()); // CONTROL_CREATE
        asOrg(ORG_PAY, () -> controlService.addMapping(id, ControlFramework.ISO27001, "A.12.4.1", "m")); // CONTROL_MAP
        asOrg(ORG_PAY, () -> controlService.retire(id, "admin")); // CONTROL_RETIRE

        ChainVerifyResult r = asOrg(ORG_PAY, () -> hashChainService.verify(ORG_PAY));
        assertTrue(r.valid(), "留痕后链应校验通过");
        assertEquals(3, r.count(), "应有 3 条留痕（定义 + 映射 + 停用）");
    }

    // ---------- B20 控件测试复用 ----------

    @Test
    void b20_有效未过期的EFFECTIVE可复用_失效则不可复用() {
        Long id = asOrg(ORG_PAY, () -> controlService.create(ORG_PAY, "CTL-T", "访问复核控件",
                "季度访问复核", "访问控制", "sec", "creator").getId());

        // 未测试 → 无可复用结论
        assertNull(asOrg(ORG_PAY, () -> controlService.reusableTest(id)), "未测试应无可复用结论");

        // 记一条 EFFECTIVE、有效期 90 天后 → 可复用
        asOrg(ORG_PAY, () -> controlService.recordTest(id, "OPERATING", "EFFECTIVE",
                java.time.LocalDate.now().plusDays(90), "季度测试通过", "auditor"));
        var reuse = asOrg(ORG_PAY, () -> controlService.reusableTest(id));
        assertNotNull(reuse, "有效且未过期的 EFFECTIVE 应可复用");
        assertEquals("EFFECTIVE", reuse.getResult());
        assertEquals("OPERATING", reuse.getTestType());

        // 记一条 DEFICIENT（更晚有效期）→ 不影响复用（复用只认 EFFECTIVE），仍返回那条 EFFECTIVE
        asOrg(ORG_PAY, () -> controlService.recordTest(id, "DESIGN", "DEFICIENT",
                java.time.LocalDate.now().plusDays(180), "设计缺陷", "auditor"));
        assertEquals("EFFECTIVE", asOrg(ORG_PAY, () -> controlService.reusableTest(id)).getResult(),
                "复用只认 EFFECTIVE，DEFICIENT 不参与");
        assertEquals(2, asOrg(ORG_PAY, () -> controlService.listTests(id)).size(), "测试历史 2 条");
    }

    @Test
    void b20_过期的EFFECTIVE不可复用() {
        Long id = asOrg(ORG_PAY, () -> controlService.create(ORG_PAY, "CTL-EXP", "过期控件",
                "x", "加密", null, "creator").getId());
        // EFFECTIVE 但有效期昨天 → 已过期，不可复用
        asOrg(ORG_PAY, () -> controlService.recordTest(id, "OPERATING", "EFFECTIVE",
                java.time.LocalDate.now().minusDays(1), "去年测的", "auditor"));
        assertNull(asOrg(ORG_PAY, () -> controlService.reusableTest(id)), "过期 EFFECTIVE 不可复用，须重测");
    }

    @Test
    void b20_非ACTIVE控件不可测_组织隔离() {
        Long id = asOrg(ORG_PAY, () -> controlService.create(ORG_PAY, "CTL-R", "R", "x", "日志", null, "creator").getId());
        asOrg(ORG_PAY, () -> controlService.retire(id, "admin"));
        // 停用控件不可测试
        assertThrows(IllegalStateException.class, () -> runAsOrg(ORG_PAY, () ->
                controlService.recordTest(id, "OPERATING", "EFFECTIVE", java.time.LocalDate.now().plusDays(30), null, "a")));

        // 非法结论被拒
        Long id2 = asOrg(ORG_PAY, () -> controlService.create(ORG_PAY, "CTL-R2", "R2", "x", "日志", null, "creator").getId());
        assertThrows(IllegalArgumentException.class, () -> runAsOrg(ORG_PAY, () ->
                controlService.recordTest(id2, "OPERATING", "GREAT", java.time.LocalDate.now().plusDays(30), null, "a")));

        // 隔离：org13 看不到 org12 控件 → recordTest 不可见即不存在
        assertThrows(IllegalArgumentException.class, () -> runAsOrg(ORG_CF, () ->
                controlService.recordTest(id2, "OPERATING", "EFFECTIVE", java.time.LocalDate.now().plusDays(30), null, "a")));
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
