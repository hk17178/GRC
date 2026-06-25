package com.mandao.grc.modules.workflow;

import com.mandao.grc.modules.audit.HashChainService;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.variable.api.history.HistoricVariableInstance;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 可配置审批运行时引擎：按「组织×业务类型」当前 ACTIVE 的审批流发起/处置审批。
 *
 * 与隔离/事务：方法 @Transactional 且位于 modules 包 → OrgScopeAspect 注入 visible_orgs；
 * Flowable 与业务共用 grc_app DataSource + Spring 事务 → 审批流转、approval_instance/task_log 写入、
 * 哈希链留痕【同事务原子】。各步审批继续上防篡改哈希链（红线不变）。
 *
 * 发起时按画布图为多实例审批节点注入 approvers_{key} 集合；计数器由流程 startEvent 监听预置。
 * 结论以流程变量 approvalOutcome(APPROVED/REJECTED) 记录，实例结束时回写 approval_instance.status，
 * 业务方据此推进自身状态机。
 */
@Service
public class ApprovalEngine {

    private final ApprovalFlowRepository flowRepo;
    private final ApprovalFlowService flowService;
    private final ApprovalInstanceRepository instanceRepo;
    private final ApprovalTaskLogRepository logRepo;
    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final HistoryService historyService;
    private final HashChainService hashChainService;

    public ApprovalEngine(ApprovalFlowRepository flowRepo, ApprovalFlowService flowService,
                          ApprovalInstanceRepository instanceRepo, ApprovalTaskLogRepository logRepo,
                          RuntimeService runtimeService, TaskService taskService,
                          HistoryService historyService, HashChainService hashChainService) {
        this.flowRepo = flowRepo;
        this.flowService = flowService;
        this.instanceRepo = instanceRepo;
        this.logRepo = logRepo;
        this.runtimeService = runtimeService;
        this.taskService = taskService;
        this.historyService = historyService;
        this.hashChainService = hashChainService;
    }

    /** 发起审批：按当前生效流程启动实例。无生效流程则抛 IllegalStateException(409)。 */
    @Transactional
    public ApprovalInstance submit(ApprovalBizType bizType, Long bizId, Long orgId, String submitter) {
        ApprovalFlow flow = flowRepo.findFirstByBizTypeAndStatus(bizType, FlowStatus.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("未配置生效的审批流：" + bizType + "（请先在审批流配置中发布一套）"));
        FlowGraph graph = flowService.parse(flow.getGraphJson());

        // 为多实例审批节点注入审批人集合（MI collection）
        Map<String, Object> vars = new HashMap<>();
        for (FlowGraph.FlowNode n : graph.nodes()) {
            if (n.type() == NodeType.APPROVAL && n.approverRefs() != null) {
                vars.put("approvers_" + n.key(), n.approverRefs());
            }
        }

        String businessKey = bizType + ":" + bizId;
        String pid = runtimeService.startProcessInstanceByKey(flow.getBpmnKey(), businessKey, vars).getId();

        ApprovalInstance inst = new ApprovalInstance(orgId, flow.getId(), flow.getVersion(), bizType, bizId, submitter);
        inst.bindProcess(pid);
        inst = instanceRepo.save(inst);

        hashChainService.append(orgId, "APPROVAL_SUBMIT", submitter, businessKey,
                "发起审批 flow=" + flow.getId() + " v" + flow.getVersion() + " instance=" + pid);
        // 若流程无任务直接结束（极端配置），同步回写
        syncOutcomeIfEnded(inst);
        return inst;
    }

    /** 处置某审批任务：完成任务 + 记流水 + 留痕；实例结束则回写最终状态。 */
    @Transactional
    public ApprovalInstance decide(String taskId, ApprovalDecision decision, String approver, String comment) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new IllegalArgumentException("审批任务不存在或已处理：" + taskId);
        }
        ApprovalInstance inst = instanceRepo.findByProcessInstanceId(task.getProcessInstanceId())
                .orElseThrow(() -> new IllegalStateException("找不到对应审批实例：pid=" + task.getProcessInstanceId()));

        String nodeKey = task.getTaskDefinitionKey();
        String nodeName = task.getName();
        String d = decision == ApprovalDecision.APPROVED ? "APPROVE" : "REJECT";
        taskService.complete(taskId, Map.of("decision", d));

        logRepo.save(new ApprovalTaskLog(inst.getOrgId(), inst.getId(), nodeKey, nodeName, approver, d, comment));
        hashChainService.append(inst.getOrgId(), "APPROVAL_DECIDE", approver, inst.getBizType() + ":" + inst.getBizId(),
                "节点=" + nodeName + " 结论=" + d + (comment == null || comment.isBlank() ? "" : " 意见：" + comment));

        syncOutcomeIfEnded(inst);
        return inst;
    }

    /** 取某业务对象当前运行中的审批任务。 */
    @Transactional(readOnly = true)
    public List<Task> activeTasks(ApprovalBizType bizType, Long bizId) {
        return instanceRepo.findFirstByBizTypeAndBizIdAndStatus(bizType, bizId, InstanceStatus.RUNNING)
                .map(i -> taskService.createTaskQuery().processInstanceId(i.getProcessInstanceId()).list())
                .orElse(List.of());
    }

    /** 列出某审批组的待办任务。 */
    @Transactional(readOnly = true)
    public List<Task> pendingTasks(String approverGroup) {
        return taskService.createTaskQuery().taskCandidateGroup(approverGroup)
                .orderByTaskCreateTime().asc().list();
    }

    /** 某业务对象的审批实例（若有）。 */
    @Transactional(readOnly = true)
    public ApprovalInstance instanceOf(ApprovalBizType bizType, Long bizId) {
        return instanceRepo.findFirstByBizTypeAndBizIdAndStatus(bizType, bizId, InstanceStatus.RUNNING)
                .orElse(null);
    }

    /** 某实例的决定流水。 */
    @Transactional(readOnly = true)
    public List<ApprovalTaskLog> logs(Long instanceId) {
        return logRepo.findByInstanceIdOrderByIdAsc(instanceId);
    }

    // ---------- 内部 ----------

    /** 流程已结束则读 approvalOutcome 回写实例最终状态。 */
    private void syncOutcomeIfEnded(ApprovalInstance inst) {
        HistoricProcessInstance hpi = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(inst.getProcessInstanceId()).singleResult();
        if (hpi == null || hpi.getEndTime() == null) {
            return; // 仍在运行
        }
        HistoricVariableInstance var = historyService.createHistoricVariableInstanceQuery()
                .processInstanceId(inst.getProcessInstanceId()).variableName("approvalOutcome").singleResult();
        String outcome = var == null || var.getValue() == null ? "REJECTED" : String.valueOf(var.getValue());
        inst.end("APPROVED".equals(outcome) ? InstanceStatus.APPROVED : InstanceStatus.REJECTED);
        instanceRepo.save(inst);
    }
}
