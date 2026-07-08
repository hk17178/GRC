package com.mandao.grc.modules.workflow;

import com.mandao.grc.modules.audit.HashChainService;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.variable.api.history.HistoricVariableInstance;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作流（审批）服务（Phase A 横向能力）：把 Flowable 流程引擎封装成「业务无关」的审批入口，
 * 供 M1 制度发布、M2 风险接受、M8 UAR/SoD 例外、变更、监管报送等模块复用，统一审批与留痕。
 *
 * ===== 与隔离/事务的关系（关键）=====
 * 本类位于 {@code com.mandao.grc.modules} 包下且方法带 @Transactional，故
 * {@link com.mandao.grc.common.isolation.OrgScopeAspect} 会在事务内注入 app.visible_orgs；
 * 其调用的 {@link HashChainService#append}（写 RLS 表 operation_log）因此受同一可见域约束、留痕正确。
 * Flowable 引擎与业务共用同一 DataSource(grc_app) 与 Spring 事务 → 审批流转与业务写入【同事务原子】。
 * 引擎表 ACT_* 无 org_id、不挂 RLS，仅做编排；组织隔离仍由各业务实体层 RLS 保证。
 *
 * ===== 通用审批流 genericApproval =====
 * 提交 → 审批(userTask) → 结束。业务定位/组织以流程变量 bizType/bizId/orgId 携带；
 * 审批人按 approverGroup（候选组）派单；结论以变量 decision(APPROVED/REJECTED) 记录。
 *
 * 设计依据：CR-001/CR-003（审批与多域控）、D1-4 详细设计、D2-5；详见 generic-approval.bpmn20.xml。
 */
@Service
public class WorkflowService {

    /** 平台内置的通用单级审批流 key（对应 generic-approval.bpmn20.xml 的 process id）。 */
    public static final String GENERIC_APPROVAL = "genericApproval";

    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final HistoryService historyService;
    private final org.flowable.engine.RepositoryService repositoryService;
    private final HashChainService hashChainService;
    private final ProcessBindingService processBindingService;
    private final ProcessLaunchRepository processLaunchRepository;

    public WorkflowService(RuntimeService runtimeService,
                           TaskService taskService,
                           HistoryService historyService,
                           org.flowable.engine.RepositoryService repositoryService,
                           HashChainService hashChainService,
                           ProcessBindingService processBindingService,
                           ProcessLaunchRepository processLaunchRepository) {
        this.runtimeService = runtimeService;
        this.taskService = taskService;
        this.historyService = historyService;
        this.repositoryService = repositoryService;
        this.hashChainService = hashChainService;
        this.processBindingService = processBindingService;
        this.processLaunchRepository = processLaunchRepository;
    }

    /**
     * 发起审批：启动一个 genericApproval 流程实例，携带业务定位与组织上下文，并留痕。
     *
     * @param bizType       业务类型（如 POLICY、RISK_ACCEPTANCE、UAR_EXCEPTION）
     * @param bizId         业务实体 id
     * @param orgId         归属组织（用于留痕与后续按域查询；须在当前 visible_orgs 内）
     * @param approverGroup 审批候选组（按角色/组织派单）
     * @param submitter     提交人（留痕 actor）
     * @return 流程实例 id（业务方可据此回写、查询结论）
     */
    @Transactional
    public String submit(String bizType, Long bizId, Long orgId, String approverGroup, String submitter) {
        return submit(bizType, bizId, orgId, approverGroup, submitter, Map.of());
    }

    /**
     * 发起审批（H-06 条件化选流程）：按 {@code routingContext} 解析流程绑定——命中且该流程已部署则用绑定流程，
     * 否则回落通用审批流；并把本次选中的 process_def_key + version（+binding_id）固化到 process_launch
     * （后续改绑定不影响在途单据）。审批节点仍走本引擎，不绕职责分离。
     *
     * @param routingContext 条件分流上下文（如 {level:HIGH}），无需分流时传空 Map
     */
    @Transactional
    public String submit(String bizType, Long bizId, Long orgId, String approverGroup, String submitter,
                         Map<String, Object> routingContext) {
        // 条件化选流程：解析绑定，命中且已部署→用绑定流程，否则回落 genericApproval
        ProcessBindingService.ProcessSnapshot snap = processBindingService.resolve(bizType, routingContext);
        String effectiveKey = GENERIC_APPROVAL;
        Long bindingId = null;
        int version;
        if (snap != null && isDeployed(snap.processDefKey())) {
            effectiveKey = snap.processDefKey();
            bindingId = snap.bindingId();
            version = snap.processVersion();
        } else {
            version = deployedVersion(GENERIC_APPROVAL);
        }

        String businessKey = businessKey(bizType, bizId);
        Map<String, Object> vars = new HashMap<>();
        vars.put("bizType", bizType);
        vars.put("bizId", bizId);
        vars.put("orgId", orgId);
        vars.put("approverGroup", approverGroup);
        vars.put("submitter", submitter);

        String instanceId = runtimeService
                .startProcessInstanceByKey(effectiveKey, businessKey, vars)
                .getId();

        // 版本快照固化（H-06）：记录发起时点用的流程定义 key+version，供在途单据回查、与后续改绑定解耦
        processLaunchRepository.save(new ProcessLaunch(orgId, bizType, bizId, effectiveKey, version,
                bindingId, instanceId, submitter));

        hashChainService.append(orgId, "WORKFLOW_SUBMIT", submitter, businessKey,
                "发起审批 process=" + effectiveKey + " v" + version + " instance=" + instanceId
                        + " approverGroup=" + approverGroup);
        return instanceId;
    }

    /** 查某单据的流程发起快照（最新在前）。走服务层以经切面注入 visible_orgs + RLS 裁剪。 */
    @Transactional(readOnly = true)
    public List<ProcessLaunch> launches(String bizType, Long bizId) {
        return processLaunchRepository.findByBizTypeAndBizIdOrderByIdDesc(bizType, bizId);
    }

    /** 该 key 的流程定义是否已部署（未部署的绑定流程回落通用审批流，避免启动失败）。 */
    private boolean isDeployed(String key) {
        return repositoryService.createProcessDefinitionQuery().processDefinitionKey(key).count() > 0;
    }

    /** 取某 key 已部署的最新版本号（用于回落通用审批流时固化版本）。 */
    private int deployedVersion(String key) {
        org.flowable.engine.repository.ProcessDefinition def = repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey(key).latestVersion().singleResult();
        return def == null ? 1 : def.getVersion();
    }

    /**
     * 取某业务对象当前进行中的审批任务（无运行中实例/任务则返回 null）。
     * 用 businessKey={bizType}:{bizId} 定位运行中的流程实例，再取其待办任务——
     * 业务模块据此用业务 id 处置审批，无需在业务实体上额外存流程实例 id。
     * 注：运行时查询只命中【未结束】实例；驳回后旧实例转历史，重新提交会建新实例，故恒取到当前那条。
     */
    @Transactional(readOnly = true)
    public Task activeTask(String bizType, Long bizId) {
        ProcessInstance pi = runtimeService.createProcessInstanceQuery()
                .processInstanceBusinessKey(businessKey(bizType, bizId))
                .singleResult();
        if (pi == null) {
            return null;
        }
        return taskService.createTaskQuery().processInstanceId(pi.getId()).singleResult();
    }

    /** 列出某候选组的待办审批任务（按创建时间升序）。 */
    @Transactional(readOnly = true)
    public List<Task> pendingTasks(String approverGroup) {
        return taskService.createTaskQuery()
                .taskCandidateGroup(approverGroup)
                .orderByTaskCreateTime().asc()
                .list();
    }

    /**
     * 处置审批任务：记录结论与审批人，完成任务并推进流程；留痕。
     *
     * @param taskId   审批任务 id
     * @param decision 审批结论（通过/驳回）
     * @param approver 审批人（留痕 actor）
     * @param comment  审批意见（可空）
     */
    @Transactional
    public void decide(String taskId, ApprovalDecision decision, String approver, String comment) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new IllegalArgumentException("审批任务不存在或已处理：taskId=" + taskId);
        }
        // 从任务变量取业务定位/组织，用于留痕（变量在 submit 时已置于流程实例上）
        long orgId = asLong(taskService.getVariable(taskId, "orgId"));
        String bizType = (String) taskService.getVariable(taskId, "bizType");
        long bizId = asLong(taskService.getVariable(taskId, "bizId"));
        String businessKey = businessKey(bizType, bizId);

        Map<String, Object> vars = new HashMap<>();
        vars.put("decision", decision.name());
        vars.put("approver", approver);
        vars.put("approveComment", comment == null ? "" : comment);
        taskService.complete(taskId, vars);

        hashChainService.append(orgId, "WORKFLOW_DECIDE", approver, businessKey,
                "审批结论=" + decision.name() + (comment == null || comment.isBlank() ? "" : "，意见：" + comment));
    }

    /**
     * 读取审批结论（流程结束后供业务方推进自身状态机）。
     * 未决（任务未完成）或无该变量时返回 null。
     */
    @Transactional(readOnly = true)
    public ApprovalDecision outcome(String processInstanceId) {
        HistoricVariableInstance var = historyService.createHistoricVariableInstanceQuery()
                .processInstanceId(processInstanceId)
                .variableName("decision")
                .singleResult();
        if (var == null || var.getValue() == null) {
            return null;
        }
        return ApprovalDecision.valueOf(var.getValue().toString());
    }

    /** 流程实例是否已结束（无运行中实例即视为已结束）。 */
    @Transactional(readOnly = true)
    public boolean isEnded(String processInstanceId) {
        HistoricProcessInstance hpi = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        return hpi != null && hpi.getEndTime() != null;
    }

    // ---------- 内部辅助 ----------

    /** 业务键统一格式 "{bizType}:{bizId}"，便于按业务对象检索流程与留痕。 */
    private String businessKey(String bizType, Long bizId) {
        return bizType + ":" + bizId;
    }

    /** Flowable 变量数值可能以 Integer/Long 返回，统一取 long。 */
    private long asLong(Object v) {
        if (v instanceof Number n) {
            return n.longValue();
        }
        throw new IllegalStateException("流程变量非数值类型，无法转 long：" + v);
    }
}
