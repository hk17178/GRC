package com.mandao.grc;

import com.mandao.grc.common.isolation.IsolationContext;
import com.mandao.grc.modules.workflow.ApprovalBizType;
import com.mandao.grc.modules.workflow.ApprovalFlowService;
import com.mandao.grc.modules.workflow.ApproverType;
import com.mandao.grc.modules.workflow.CountersignMode;
import com.mandao.grc.modules.workflow.FlowGraph;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 可配置审批流·运行时集成测试（P1.2a：真实 PG + Flowable，编译→部署→执行）。验证：
 *  1) 或签(任一)→会签(全部) 串行流：编译部署后，或签节点一人通过即过、会签节点须全通过 → APPROVED；
 *  2) 驳回：首节点驳回 → 直达驳回结束 → REJECTED。
 *
 * 证明 graph→BPMN 编译器对 单审批/会签/或签/隐式驳回/结论回写 的端到端正确性。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "grc.scheduler.enabled=false")
@Testcontainers
class ApprovalFlowRuntimeTest {

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

    @Autowired private ApprovalFlowService service;
    @Autowired private RuntimeService runtimeService;
    @Autowired private TaskService taskService;
    @Autowired private HistoryService historyService;

    private static final long ORG_PAY = 12L;

    @BeforeEach
    void clean() throws Exception {
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement()) {
            s.executeUpdate("TRUNCATE approval_flow, approval_instance, approval_task_log RESTART IDENTITY CASCADE");
        }
    }

    @AfterEach
    void clearContext() {
        IsolationContext.clear();
    }

    /** 或签(CHECKER,RISK_OWNER 任一)→ 会签(RISK_OWNER,AUDITOR 全部)→ 通过结束。 */
    private FlowGraph graph() {
        return new FlowGraph(
                List.of(
                        new FlowGraph.FlowNode("start", com.mandao.grc.modules.workflow.NodeType.START, null, null, null, null, null, null, null, null),
                        new FlowGraph.FlowNode("n1", com.mandao.grc.modules.workflow.NodeType.APPROVAL, "部门或签", ApproverType.ROLE,
                                List.of("CHECKER", "RISK_OWNER"), CountersignMode.ANY, 1, null, null, null),
                        new FlowGraph.FlowNode("n2", com.mandao.grc.modules.workflow.NodeType.APPROVAL, "合规会签", ApproverType.ROLE,
                                List.of("RISK_OWNER", "AUDITOR"), CountersignMode.ALL, null, null, null, null),
                        new FlowGraph.FlowNode("end", com.mandao.grc.modules.workflow.NodeType.END, null, null, null, null, null, null, null, "APPROVED")),
                List.of(new FlowGraph.FlowEdge("start", "n1", null),
                        new FlowGraph.FlowEdge("n1", "n2", null),
                        new FlowGraph.FlowEdge("n2", "end", null)));
    }

    private String publishAndStart() {
        Long id = asOrg(ORG_PAY, () -> service.createDraft(ORG_PAY, ApprovalBizType.POLICY_PUBLISH, "测试流", graph()).getId());
        asOrg(ORG_PAY, () -> service.publish(id));
        Map<String, Object> vars = new HashMap<>();
        vars.put("approvers_n1", List.of("CHECKER", "RISK_OWNER"));
        vars.put("approvers_n2", List.of("RISK_OWNER", "AUDITOR"));
        return runtimeService.startProcessInstanceByKey("approvalFlow" + id, "POLICY:1", vars).getId();
    }

    private void completeAll(String pi, String decision) {
        for (Task t : taskService.createTaskQuery().processInstanceId(pi).list()) {
            taskService.complete(t.getId(), Map.of("decision", decision));
        }
    }

    private String outcome(String pi) {
        var v = historyService.createHistoricVariableInstanceQuery()
                .processInstanceId(pi).variableName("approvalOutcome").singleResult();
        return v == null ? null : String.valueOf(v.getValue());
    }

    @Test
    void 或签一人过_会签全过_最终通过() {
        String pi = publishAndStart();
        // n1 或签：2 个任务，只通过 1 个即应过节点
        List<Task> n1 = taskService.createTaskQuery().processInstanceId(pi).list();
        assertEquals(2, n1.size(), "或签应派 2 个任务");
        taskService.complete(n1.get(0).getId(), Map.of("decision", "APPROVE"));
        // 进入 n2 会签：2 个任务都须通过
        List<Task> n2 = taskService.createTaskQuery().processInstanceId(pi).list();
        assertEquals(2, n2.size(), "会签应派 2 个任务");
        completeAll(pi, "APPROVE");
        // 实例结束、结论 APPROVED
        assertEquals(0, runtimeService.createProcessInstanceQuery().processInstanceId(pi).count(), "实例应结束");
        assertEquals("APPROVED", outcome(pi));
    }

    @Test
    void 首节点驳回_直达驳回结束() {
        String pi = publishAndStart();
        // n1 或签 2 人全驳回 → 节点不过 → 驳回结束
        completeAll(pi, "REJECT");
        assertEquals(0, runtimeService.createProcessInstanceQuery().processInstanceId(pi).count(), "实例应结束");
        assertEquals("REJECTED", outcome(pi));
    }

    private <T> T asOrg(long orgId, Supplier<T> action) {
        IsolationContext.set(List.of(orgId));
        try {
            return action.get();
        } finally {
            IsolationContext.clear();
        }
    }
}
