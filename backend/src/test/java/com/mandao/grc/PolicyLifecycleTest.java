package com.mandao.grc;

import com.mandao.grc.common.isolation.IsolationContext;
import com.mandao.grc.modules.audit.ChainVerifyResult;
import com.mandao.grc.modules.audit.HashChainService;
import com.mandao.grc.modules.policy.Policy;
import com.mandao.grc.modules.policy.PolicyService;
import com.mandao.grc.modules.policy.PolicyStatus;
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
 * M1 制度体系生命周期集成测试（真实 PG + 应用切面 + RLS）。验证：
 *  1) 正常生命周期 DRAFT→PENDING_APPROVAL→PUBLISHED→ARCHIVED 通过；
 *  2) 非法流转（DRAFT 直接 archive、PUBLISHED 再 submit）被拒；
 *  3) 组织隔离：org12 的制度，在 org13 上下文中看不到；
 *  4) 留痕：流转后对应 org 的哈希链 verify 通过且有记录；
 *  5) 签署确认：PUBLISHED 后 signoff 成功，重复 signoff 被唯一约束拒。
 *
 * scheduler.enabled=false 关闭定时器，避免无关后台扫描干扰本测试。
 * 设计依据：D1-2 制度生命周期、D1-3 §5.1/§8、D2-5。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "grc.scheduler.enabled=false")
@Testcontainers
class PolicyLifecycleTest {

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
    private PolicyService policyService;

    @Autowired
    private HashChainService hashChainService;

    private static final long ORG_PAY = 12L;   // 支付子公司
    private static final long ORG_CF = 13L;     // 消费金融

    /**
     * 每用例前清空制度相关表与操作日志（owner 连接，绕 RLS；grc_app 无删权）。
     * 静态容器在多个用例间共享，必须隔离测试数据。
     */
    @BeforeEach
    void clean() throws Exception {
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement()) {
            s.executeUpdate("TRUNCATE policy_signoff, policy, operation_log RESTART IDENTITY CASCADE");
        }
    }

    @AfterEach
    void clearContext() {
        IsolationContext.clear();
    }

    // ---------- 用例 ----------

    @Test
    void 正常生命周期_草稿到归档全程通过() {
        Long id = asOrg(ORG_PAY, () ->
                policyService.create(ORG_PAY, "POL-001", "信息安全管理制度", "正文……", "drafter").getId());

        assertEquals(PolicyStatus.PENDING_APPROVAL,
                asOrg(ORG_PAY, () -> policyService.submitForApproval(id, "drafter").getStatus()));
        assertEquals(PolicyStatus.PUBLISHED,
                asOrg(ORG_PAY, () -> policyService.approve(id, "approver").getStatus()));
        assertEquals(PolicyStatus.ARCHIVED,
                asOrg(ORG_PAY, () -> policyService.archive(id, "admin").getStatus()));
    }

    @Test
    void 驳回回到草稿后可再次提交() {
        Long id = asOrg(ORG_PAY, () ->
                policyService.create(ORG_PAY, "POL-002", "数据分类分级制度", "正文", "drafter").getId());
        asOrg(ORG_PAY, () -> policyService.submitForApproval(id, "drafter"));

        assertEquals(PolicyStatus.DRAFT,
                asOrg(ORG_PAY, () -> policyService.reject(id, "approver", "需补充落地细则").getStatus()));
        // 驳回回到 DRAFT 后应可再次提交
        assertEquals(PolicyStatus.PENDING_APPROVAL,
                asOrg(ORG_PAY, () -> policyService.submitForApproval(id, "drafter").getStatus()));
    }

    @Test
    void 非法流转_草稿直接归档被拒() {
        Long id = asOrg(ORG_PAY, () ->
                policyService.create(ORG_PAY, "POL-003", "X", "Y", "drafter").getId());
        // DRAFT 态不可直接 archive（archive 仅允许从 PUBLISHED）
        assertThrows(IllegalStateException.class,
                () -> runAsOrg(ORG_PAY, () -> policyService.archive(id, "admin")));
    }

    @Test
    void 非法流转_已发布再提交审批被拒() {
        Long id = asOrg(ORG_PAY, () ->
                policyService.create(ORG_PAY, "POL-004", "X", "Y", "drafter").getId());
        asOrg(ORG_PAY, () -> policyService.submitForApproval(id, "drafter"));
        asOrg(ORG_PAY, () -> policyService.approve(id, "approver"));
        // PUBLISHED 态不可再 submit（submit 仅允许从 DRAFT）
        assertThrows(IllegalStateException.class,
                () -> runAsOrg(ORG_PAY, () -> policyService.submitForApproval(id, "drafter")));
    }

    @Test
    void 组织隔离_org12的制度org13看不到() {
        asOrg(ORG_PAY, () ->
                policyService.create(ORG_PAY, "POL-101", "仅支付可见", "正文", "drafter"));

        // org12 上下文能看到自己的制度
        List<Policy> payView = asOrg(ORG_PAY, () -> policyService.list());
        assertEquals(1, payView.size(), "org12 应看到自己的 1 条制度");

        // org13 上下文看不到 org12 的制度（RLS 裁剪）
        List<Policy> cfView = asOrg(ORG_CF, () -> policyService.list());
        assertTrue(cfView.isEmpty(), "org13 不应看到 org12 的制度");
    }

    @Test
    void 留痕_流转后哈希链校验通过且有记录() {
        Long id = asOrg(ORG_PAY, () ->
                policyService.create(ORG_PAY, "POL-201", "X", "Y", "drafter").getId());
        asOrg(ORG_PAY, () -> policyService.submitForApproval(id, "drafter"));
        asOrg(ORG_PAY, () -> policyService.approve(id, "approver"));

        ChainVerifyResult r = asOrg(ORG_PAY, () -> hashChainService.verify(ORG_PAY));
        assertTrue(r.valid(), "流转留痕后链应校验通过");
        // CREATE + SUBMIT + APPROVE = 3 条留痕
        assertEquals(3, r.count(), "应有 3 条制度操作留痕");
    }

    @Test
    void 签署_发布后可签署且重复签署被拒() {
        Long id = asOrg(ORG_PAY, () ->
                policyService.create(ORG_PAY, "POL-301", "X", "Y", "drafter").getId());
        asOrg(ORG_PAY, () -> policyService.submitForApproval(id, "drafter"));
        asOrg(ORG_PAY, () -> policyService.approve(id, "approver"));

        // PUBLISHED 后签署成功
        asOrg(ORG_PAY, () -> policyService.signoff(id, "zhangsan"));

        // 同一人重复签署被 UNIQUE(policy_id, signer) 约束拒绝
        assertThrows(Exception.class,
                () -> runAsOrg(ORG_PAY, () -> policyService.signoff(id, "zhangsan")));
    }

    @Test
    void 签署_未发布制度不可签署() {
        Long id = asOrg(ORG_PAY, () ->
                policyService.create(ORG_PAY, "POL-302", "X", "Y", "drafter").getId());
        // DRAFT 态不可签署
        assertThrows(IllegalStateException.class,
                () -> runAsOrg(ORG_PAY, () -> policyService.signoff(id, "zhangsan")));
    }

    // ---------- 测试辅助：在指定 org 可见上下文中执行 ----------

    /**
     * 在 orgId 的可见上下文中执行有返回值的动作。
     * 用 IsolationContext.set 设可见 org（仿 HashChainTest 范式），切面据此注入 visible_orgs。
     */
    private <T> T asOrg(long orgId, Supplier<T> action) {
        IsolationContext.set(List.of(orgId));
        try {
            return action.get();
        } finally {
            IsolationContext.clear();
        }
    }

    /**
     * 在 orgId 的可见上下文中执行无返回值的动作（供 assertThrows 包裹时使用，
     * 以便业务异常向外抛出而上下文仍被清理）。
     */
    private void runAsOrg(long orgId, Callable<?> action) throws Exception {
        IsolationContext.set(List.of(orgId));
        try {
            action.call();
        } finally {
            IsolationContext.clear();
        }
    }
}
