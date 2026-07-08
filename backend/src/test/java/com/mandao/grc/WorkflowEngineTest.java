package com.mandao.grc;

import com.mandao.grc.common.isolation.IsolationContext;
import com.mandao.grc.modules.audit.ChainVerifyResult;
import com.mandao.grc.modules.audit.HashChainService;
import com.mandao.grc.modules.workflow.ApprovalDecision;
import com.mandao.grc.modules.workflow.WorkflowService;
import org.flowable.engine.RepositoryService;
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
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Flowable 工作流引擎内核集成测试（Phase A 横向审批能力）。验证：
 *  1) 引擎随应用启动、通用审批流 genericApproval 自动部署；
 *  2) 提交→待办任务→处置(通过) 全程跑通，流程结束、结论为 APPROVED；
 *  3) 驳回路径结论为 REJECTED；
 *  4) 引擎与业务共用同一 DataSource(grc_app)/事务，审批留痕落 operation_log 且哈希链校验通过。
 *
 * 说明：ACT_* 引擎表无 org_id/不挂 RLS，仅做编排；隔离由业务实体层 RLS 保证。
 * 各用例用独立 approverGroup 隔离待办查询，免清引擎表；@BeforeEach 仅清 operation_log 以稳定留痕计数。
 *
 * 设计依据：CR-001/CR-003、D1-4、D2-5；流程见 generic-approval.bpmn20.xml。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "grc.scheduler.enabled=false")
@Testcontainers
class WorkflowEngineTest {

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
    private WorkflowService workflowService;

    @Autowired
    private HashChainService hashChainService;

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private com.mandao.grc.modules.workflow.ProcessBindingService processBindingService;

    @Autowired
    private com.mandao.grc.modules.workflow.ProcessLaunchRepository processLaunchRepository;

    private static final long ORG_PAY = 12L; // 支付子公司

    /** 每用例前清空操作日志（owner 连接绕 RLS），稳定留痕计数。引擎表不清，靠独立候选组隔离。 */
    @BeforeEach
    void clean() throws Exception {
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement()) {
            s.executeUpdate("TRUNCATE operation_log, process_binding, process_launch RESTART IDENTITY");
        }
    }

    @AfterEach
    void clearContext() {
        IsolationContext.clear();
    }

    @Test
    void 引擎启动_通用审批流已部署() {
        long count = repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey(WorkflowService.GENERIC_APPROVAL)
                .count();
        assertTrue(count >= 1, "genericApproval 流程应已随启动自动部署");
    }

    @Test
    void 审批通过_提交到处置全程_结论APPROVED且留痕() {
        String group = "grp-policy-pay";
        // 提交（org12 上下文，留痕需可见域）
        String instanceId = asOrg(ORG_PAY, () ->
                workflowService.submit("POLICY", 555L, ORG_PAY, group, "alice"));
        assertNotNull(instanceId);

        // 待办应有一条该候选组任务；流程尚未结束
        List<Task> tasks = asOrg(ORG_PAY, () -> workflowService.pendingTasks(group));
        assertEquals(1, tasks.size(), "应有 1 条待办审批任务");
        assertFalse(asOrg(ORG_PAY, () -> workflowService.isEnded(instanceId)), "处置前流程不应结束");

        // 处置：通过
        String taskId = tasks.get(0).getId();
        asOrg(ORG_PAY, () -> {
            workflowService.decide(taskId, ApprovalDecision.APPROVED, "bob", "符合发布要求");
            return null;
        });

        assertTrue(asOrg(ORG_PAY, () -> workflowService.isEnded(instanceId)), "处置后流程应结束");
        assertEquals(ApprovalDecision.APPROVED, asOrg(ORG_PAY, () -> workflowService.outcome(instanceId)));

        // 留痕：WORKFLOW_SUBMIT + WORKFLOW_DECIDE 两条，链校验通过
        ChainVerifyResult r = asOrg(ORG_PAY, () -> hashChainService.verify(ORG_PAY));
        assertTrue(r.valid(), "审批留痕后链应校验通过");
        assertEquals(2, r.count(), "应有 2 条审批留痕（提交+处置）");
    }

    @Test
    void 审批驳回_结论REJECTED() {
        String group = "grp-risk-pay";
        String instanceId = asOrg(ORG_PAY, () ->
                workflowService.submit("RISK_ACCEPTANCE", 777L, ORG_PAY, group, "carol"));

        String taskId = asOrg(ORG_PAY, () -> workflowService.pendingTasks(group)).get(0).getId();
        asOrg(ORG_PAY, () -> {
            workflowService.decide(taskId, ApprovalDecision.REJECTED, "dave", "残余风险过高，不予接受");
            return null;
        });

        assertTrue(asOrg(ORG_PAY, () -> workflowService.isEnded(instanceId)));
        assertEquals(ApprovalDecision.REJECTED, asOrg(ORG_PAY, () -> workflowService.outcome(instanceId)));
    }

    // ---------- H-06 接线：发起时固化流程快照 ----------

    @Test
    void h06接线_发起记录流程快照_无绑定回落通用审批流() {
        String instanceId = asOrg(ORG_PAY, () ->
                workflowService.submit("POLICY", 5001L, ORG_PAY, "grp-h06-a", "alice"));

        var launches = asOrg(ORG_PAY, () -> workflowService.launches("POLICY", 5001L));
        assertEquals(1, launches.size(), "应固化 1 条发起快照");
        var l = launches.get(0);
        assertEquals(WorkflowService.GENERIC_APPROVAL, l.getProcessDefKey(), "无绑定→回落通用审批流");
        assertTrue(l.getProcessVersion() >= 1);
        assertEquals(instanceId, l.getProcessInstanceId());
        assertNull(l.getBindingId(), "回落时无 binding");
    }

    @Test
    void h06接线_命中绑定固化其key与版本() {
        // 建一个 POLICY 兜底绑定，指向已部署的 genericApproval，声明版本 7
        asOrg(ORG_PAY, () -> processBindingService.create(ORG_PAY, "POLICY", "制度默认审批",
                "{}", WorkflowService.GENERIC_APPROVAL, 7, 0, "admin"));

        asOrg(ORG_PAY, () -> workflowService.submit("POLICY", 5002L, ORG_PAY, "grp-h06-b", "carol"));

        var l = asOrg(ORG_PAY, () -> workflowService.launches("POLICY", 5002L)).get(0);
        assertEquals(WorkflowService.GENERIC_APPROVAL, l.getProcessDefKey());
        assertEquals(7, l.getProcessVersion(), "固化绑定声明的版本 7（改绑定不影响此在途单据快照）");
        assertNotNull(l.getBindingId(), "命中绑定应记录 binding_id");
    }

    // ---------- M8-5：审批加签/转办 ----------

    @Test
    void m8_5_转办改派_职责分离拒发起人() {
        String group = "grp-m85-a";
        asOrg(ORG_PAY, () -> workflowService.submit("POLICY", 6001L, ORG_PAY, group, "alice"));
        String taskId = asOrg(ORG_PAY, () -> workflowService.pendingTasks(group)).get(0).getId();

        // 转办给 bob（由 carol 操作）
        asOrg(ORG_PAY, () -> { workflowService.reassign(taskId, "bob", "carol"); return null; });
        assertTrue(asOrg(ORG_PAY, () -> workflowService.myPendingTasks("bob")).stream()
                .anyMatch(t -> t.getId().equals(taskId)), "转办后 bob 待办应含该任务");

        // 转办给发起人 alice → 职责分离拒
        assertThrows(IllegalStateException.class, () -> asOrg(ORG_PAY, () -> {
            workflowService.reassign(taskId, "alice", "carol");
            return null;
        }));
    }

    @Test
    void m8_5_加签追加候选人_职责分离拒发起人() {
        String group = "grp-m85-b";
        asOrg(ORG_PAY, () -> workflowService.submit("POLICY", 6002L, ORG_PAY, group, "alice"));
        String taskId = asOrg(ORG_PAY, () -> workflowService.pendingTasks(group)).get(0).getId();

        asOrg(ORG_PAY, () -> { workflowService.addSigner(taskId, "dave", "carol"); return null; });
        assertTrue(asOrg(ORG_PAY, () -> workflowService.myPendingTasks("dave")).stream()
                .anyMatch(t -> t.getId().equals(taskId)), "加签后 dave（或签候选人）应可见该任务");

        // 对发起人 alice 加签 → 职责分离拒
        assertThrows(IllegalStateException.class, () -> asOrg(ORG_PAY, () -> {
            workflowService.addSigner(taskId, "alice", "carol");
            return null;
        }));
    }

    @Test
    void m8_5_转办后原候选人处置仍推进流程() {
        String group = "grp-m85-c";
        String iid = asOrg(ORG_PAY, () -> workflowService.submit("POLICY", 6003L, ORG_PAY, group, "alice"));
        String taskId = asOrg(ORG_PAY, () -> workflowService.pendingTasks(group)).get(0).getId();
        asOrg(ORG_PAY, () -> { workflowService.reassign(taskId, "bob", "carol"); return null; });
        // 受让人 bob 处置通过 → 流程结束
        asOrg(ORG_PAY, () -> {
            workflowService.decide(taskId, ApprovalDecision.APPROVED, "bob", "同意");
            return null;
        });
        assertTrue(asOrg(ORG_PAY, () -> workflowService.isEnded(iid)));
        assertEquals(ApprovalDecision.APPROVED, asOrg(ORG_PAY, () -> workflowService.outcome(iid)));
    }

    // ---------- 测试辅助：在指定 org 可见上下文中执行 ----------

    private <T> T asOrg(long orgId, Supplier<T> action) {
        IsolationContext.set(List.of(orgId));
        try {
            return action.get();
        } finally {
            IsolationContext.clear();
        }
    }
}
