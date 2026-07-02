package com.mandao.grc;

import com.mandao.grc.common.isolation.IsolationContext;
import com.mandao.grc.modules.audit.ChainVerifyResult;
import com.mandao.grc.modules.audit.HashChainService;
import com.mandao.grc.modules.policy.Policy;
import com.mandao.grc.modules.policy.PolicyService;
import com.mandao.grc.modules.policy.PolicyStatus;
import com.mandao.grc.modules.workflow.WorkflowService;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.task.api.Task;
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
 * M1 制度体系生命周期集成测试（真实 PG + 应用切面 + RLS）。验证：
 *  1) 正常生命周期 DRAFT→REVIEW→EFFECTIVE→DEPRECATED 通过；
 *  2) 非法流转（DRAFT 直接 archive、EFFECTIVE 再 submit）被拒；
 *  3) 组织隔离：org12 的制度，在 org13 上下文中看不到；
 *  4) 留痕：流转后对应 org 的哈希链 verify 通过且有记录；
 *  5) 签署确认：EFFECTIVE 后 signoff 成功，重复 signoff 被唯一约束拒。
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

    @Autowired
    private WorkflowService workflowService;

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private HistoryService historyService;

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
            s.executeUpdate("TRUNCATE policy_ref, policy_version, policy_signoff, policy, operation_log RESTART IDENTITY CASCADE");
        }
        // 清理 Flowable 运行态/历史：本类用 RESTART IDENTITY 复用 policy id，会使各用例
        // businessKey(POLICY:{id}) 跨用例碰撞，残留的运行中实例致 activeTask 命中多条而报错。
        // 生产环境 policy id 全局唯一、无此问题；此处仅为测试隔离做净化。
        runtimeService.createProcessInstanceQuery().list()
                .forEach(pi -> runtimeService.deleteProcessInstance(pi.getId(), "用例重置"));
        historyService.createHistoricProcessInstanceQuery().list()
                .forEach(hpi -> historyService.deleteHistoricProcessInstance(hpi.getId()));
    }

    @AfterEach
    void clearContext() {
        IsolationContext.clear();
    }

    // ---------- 用例 ----------

    @Test
    void M1深度_修订快照与版本历史_元数据_引用关系() {
        // 建两个制度：A(将修订) + B(被 A 引用)
        Long a = asOrg(ORG_PAY, () ->
                policyService.create(ORG_PAY, "POL-A", "密码管理办法", "v1 正文", "drafter").getId());
        Long b = asOrg(ORG_PAY, () ->
                policyService.create(ORG_PAY, "POL-B", "密码技术实施细则", "细则正文", "drafter").getId());

        // 元数据
        var meta = asOrg(ORG_PAY, () -> policyService.updateMeta(a, "ISO27001",
                java.time.LocalDate.of(2026, 7, 1), 12, "信息安全部", "张三", "admin"));
        assertEquals("ISO27001", meta.getFramework());
        assertEquals(12, meta.getReviewCycleMonths());
        assertEquals("信息安全部", meta.getOwnerDept());

        // 引用：细则 B 引用 办法 A
        asOrg(ORG_PAY, () -> policyService.addRef(b, a, "第3章引用其密码策略", "admin"));
        assertEquals(1, asOrg(ORG_PAY, () -> policyService.refs(b)).get("outgoing").size(), "B 引用了 A");
        assertEquals(1, asOrg(ORG_PAY, () -> policyService.refs(a)).get("incoming").size(), "A 被 B 引用");

        // 生效 A → 修订：旧版进快照、版本 2、回 REVIEW
        asOrg(ORG_PAY, () -> policyService.submitForApproval(a, "drafter"));
        asOrg(ORG_PAY, () -> policyService.approve(a, "approver"));
        var revised = asOrg(ORG_PAY, () -> policyService.revise(a, "密码管理办法(修订)", "v2 正文", "新增远程办公条款", "drafter"));
        assertEquals(2, revised.getVersion(), "修订后版本应为 2");
        assertEquals(PolicyStatus.REVIEW, revised.getStatus(), "修订后应回 REVIEW 重走审批");
        var versions = asOrg(ORG_PAY, () -> policyService.versions(a));
        assertEquals(1, versions.size(), "旧版应有 1 条快照");
        assertEquals(1, versions.get(0).getVersionNo());
        assertEquals("v1 正文", versions.get(0).getContent(), "快照应保留旧版正文");

        // 草稿态不可修订
        assertThrows(IllegalStateException.class,
                () -> runAsOrg(ORG_PAY, () -> policyService.revise(b, "x", "y", null, "drafter")));
    }

    @Test
    void 正常生命周期_草稿到归档全程通过() {
        Long id = asOrg(ORG_PAY, () ->
                policyService.create(ORG_PAY, "POL-001", "信息安全管理制度", "正文……", "drafter").getId());

        assertEquals(PolicyStatus.REVIEW,
                asOrg(ORG_PAY, () -> policyService.submitForApproval(id, "drafter").getStatus()));
        assertEquals(PolicyStatus.EFFECTIVE,
                asOrg(ORG_PAY, () -> policyService.approve(id, "approver").getStatus()));
        assertEquals(PolicyStatus.DEPRECATED,
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
        assertEquals(PolicyStatus.REVIEW,
                asOrg(ORG_PAY, () -> policyService.submitForApproval(id, "drafter").getStatus()));
    }

    @Test
    void 非法流转_草稿直接归档被拒() {
        Long id = asOrg(ORG_PAY, () ->
                policyService.create(ORG_PAY, "POL-003", "X", "Y", "drafter").getId());
        // DRAFT 态不可直接 archive（archive 仅允许从 EFFECTIVE）
        assertThrows(IllegalStateException.class,
                () -> runAsOrg(ORG_PAY, () -> policyService.archive(id, "admin")));
    }

    @Test
    void 非法流转_已发布再提交审批被拒() {
        Long id = asOrg(ORG_PAY, () ->
                policyService.create(ORG_PAY, "POL-004", "X", "Y", "drafter").getId());
        asOrg(ORG_PAY, () -> policyService.submitForApproval(id, "drafter"));
        asOrg(ORG_PAY, () -> policyService.approve(id, "approver"));
        // EFFECTIVE 态不可再 submit（submit 仅允许从 DRAFT）
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
        // CREATE + SUBMIT + WORKFLOW_SUBMIT + WORKFLOW_DECIDE + APPROVE = 5 条
        //（审批走 Flowable 后，提交多出 WORKFLOW_SUBMIT、审批多出 WORKFLOW_DECIDE）
        assertEquals(5, r.count(), "应有 5 条操作留痕（含工作流提交/处置）");
    }

    @Test
    void 制度审批走工作流_提交产生待办_通过后流程结束并生效() {
        Long id = asOrg(ORG_PAY, () ->
                policyService.create(ORG_PAY, "POL-401", "审批联动制度", "正文", "drafter").getId());
        asOrg(ORG_PAY, () -> policyService.submitForApproval(id, "drafter"));

        // 提交评审后：该制度应产生一条进行中的审批任务（经 Flowable 引擎）
        Task task = asOrg(ORG_PAY, () -> workflowService.activeTask(PolicyService.BIZ_TYPE, id));
        assertNotNull(task, "提交评审后应产生审批待办任务");

        // 审批通过：制度生效，且流程结束、无进行中任务
        assertEquals(PolicyStatus.EFFECTIVE,
                asOrg(ORG_PAY, () -> policyService.approve(id, "approver").getStatus()));
        assertNull(asOrg(ORG_PAY, () -> workflowService.activeTask(PolicyService.BIZ_TYPE, id)),
                "审批通过后不应再有进行中的审批任务");
    }

    @Test
    void 签署_发布后可签署且重复签署被拒() {
        Long id = asOrg(ORG_PAY, () ->
                policyService.create(ORG_PAY, "POL-301", "X", "Y", "drafter").getId());
        asOrg(ORG_PAY, () -> policyService.submitForApproval(id, "drafter"));
        asOrg(ORG_PAY, () -> policyService.approve(id, "approver"));

        // EFFECTIVE 后签署成功
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
