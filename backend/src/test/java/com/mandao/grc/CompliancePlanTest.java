package com.mandao.grc;

import com.mandao.grc.common.isolation.IsolationContext;
import com.mandao.grc.modules.audit.ChainVerifyResult;
import com.mandao.grc.modules.audit.HashChainService;
import com.mandao.grc.modules.regulatory.CompliancePlan;
import com.mandao.grc.modules.regulatory.CompliancePlanItem;
import com.mandao.grc.modules.regulatory.CompliancePlanItemStatus;
import com.mandao.grc.modules.regulatory.CompliancePlanService;
import com.mandao.grc.modules.regulatory.CompliancePlanStatus;
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
 * 年度合规计划集成测试（真实 PG + 切面 + RLS）。验证：
 *  1) 计划生命周期：建计划→加项→下发(ACTIVE)→更新项进度→收口(CLOSED)；
 *  2) 门控：空计划不可下发、非草稿不可加项；
 *  3) 组织隔离：org12 计划，org13 看不到；
 *  4) 留痕：建计划+加项+下发哈希链校验通过且计数正确。
 *
 * 设计依据：需求文档 M11 监管事项（年度合规计划）、D2-5。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "grc.scheduler.enabled=false")
@Testcontainers
class CompliancePlanTest {

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
    private CompliancePlanService planService;

    @Autowired
    private HashChainService hashChainService;

    private static final long ORG_PAY = 12L;
    private static final long ORG_CF = 13L;
    private static final LocalDate TODAY = LocalDate.of(2026, 6, 24);

    @BeforeEach
    void clean() throws Exception {
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement()) {
            s.executeUpdate("TRUNCATE compliance_plan_item, compliance_plan, operation_log RESTART IDENTITY CASCADE");
        }
    }

    @AfterEach
    void clearContext() {
        IsolationContext.clear();
    }

    @Test
    void 计划全流程_建项下发更新进度收口() {
        Long pid = asOrg(ORG_PAY, () -> planService.create(ORG_PAY, 2026, "2026 年度合规计划", "compliance", "c").getId());
        asOrg(ORG_PAY, () -> planService.addItem(pid, "反洗钱季度报送", "合规部", TODAY.plusMonths(3), "c"));
        Long itemId = asOrg(ORG_PAY, () -> planService.addItem(pid, "等保年度测评", "安全部", TODAY.plusMonths(6), "c").getId());

        // 下发执行
        assertEquals(CompliancePlanStatus.ACTIVE, asOrg(ORG_PAY, () -> planService.activate(pid, "lead").getStatus()));

        // 计划项默认待启动；推进到完成
        List<CompliancePlanItem> items = asOrg(ORG_PAY, () -> planService.listItems(pid));
        assertEquals(2, items.size());
        assertEquals(CompliancePlanItemStatus.PENDING, items.get(0).getStatus());
        assertEquals(CompliancePlanItemStatus.DONE,
                asOrg(ORG_PAY, () -> planService.updateItemStatus(itemId, CompliancePlanItemStatus.DONE, "安全部").getStatus()));

        // 收口
        assertEquals(CompliancePlanStatus.CLOSED, asOrg(ORG_PAY, () -> planService.close(pid, "lead").getStatus()));
    }

    @Test
    void 门控_空计划不可下发_非草稿不可加项() {
        Long pid = asOrg(ORG_PAY, () -> planService.create(ORG_PAY, 2027, "空计划", null, "c").getId());
        // 空计划不可下发
        assertThrows(IllegalStateException.class, () -> runAsOrg(ORG_PAY, () -> planService.activate(pid, "lead")));
        // 下发后不可再加项
        asOrg(ORG_PAY, () -> planService.addItem(pid, "x", "部门", TODAY.plusDays(30), "c"));
        asOrg(ORG_PAY, () -> planService.activate(pid, "lead"));
        assertThrows(IllegalStateException.class,
                () -> runAsOrg(ORG_PAY, () -> planService.addItem(pid, "y", "部门", TODAY.plusDays(30), "c")));
    }

    @Test
    void 组织隔离_org12计划org13看不到() {
        asOrg(ORG_PAY, () -> planService.create(ORG_PAY, 2026, "仅支付可见", null, "c"));
        assertEquals(1, asOrg(ORG_PAY, () -> planService.list()).size(), "org12 应看到自己的 1 个计划");
        assertTrue(asOrg(ORG_CF, () -> planService.list()).isEmpty(), "org13 不应看到 org12 的计划");
    }

    @Test
    void 留痕_建计划加项下发共3条() {
        Long pid = asOrg(ORG_PAY, () -> planService.create(ORG_PAY, 2026, "H", null, "c").getId()); // CREATE
        asOrg(ORG_PAY, () -> planService.addItem(pid, "事项", "部门", TODAY.plusDays(30), "c"));        // ADD_ITEM
        asOrg(ORG_PAY, () -> planService.activate(pid, "lead"));                                       // ACTIVATE

        ChainVerifyResult r = asOrg(ORG_PAY, () -> hashChainService.verify(ORG_PAY));
        assertTrue(r.valid(), "留痕后链应校验通过");
        assertEquals(3, r.count(), "应有 3 条留痕（建计划+加项+下发）");
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
