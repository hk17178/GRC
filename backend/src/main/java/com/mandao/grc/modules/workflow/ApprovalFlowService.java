package com.mandao.grc.modules.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 审批流配置服务（P1.1：草稿管理 + 校验）。
 *
 * 隔离：方法 @Transactional 且位于 modules 包 → OrgScopeAspect 注入 visible_orgs，RLS 自动裁剪；
 * 各组织只读写自己的审批流（含集团 org1 自己的一套）。
 *
 * 草稿允许不完整（便于画布渐进编辑），仅"校验/发布"时做完整结构校验。
 * 发布(编译成 BPMN 部署)在 P1.2 接入。
 */
@Service
public class ApprovalFlowService {

    private final ApprovalFlowRepository repo;
    private final FlowGraphValidator validator;
    private final ObjectMapper objectMapper;

    public ApprovalFlowService(ApprovalFlowRepository repo, FlowGraphValidator validator, ObjectMapper objectMapper) {
        this.repo = repo;
        this.validator = validator;
        this.objectMapper = objectMapper;
    }

    /** 新建草稿。 */
    @Transactional
    public ApprovalFlow createDraft(Long orgId, ApprovalBizType bizType, String name, FlowGraph graph) {
        return repo.save(new ApprovalFlow(orgId, bizType, name, toJson(graph)));
    }

    /** 更新草稿（名称 + 画布）。 */
    @Transactional
    public ApprovalFlow updateDraft(Long id, String name, FlowGraph graph) {
        ApprovalFlow flow = get(id);
        if (flow.getStatus() == FlowStatus.RETIRED) {
            throw new IllegalStateException("已停用的流程不可编辑");
        }
        flow.updateDraft(name, toJson(graph));
        return repo.save(flow);
    }

    /** 取单个流程（不可见则视为不存在）。 */
    @Transactional(readOnly = true)
    public ApprovalFlow get(Long id) {
        return repo.findById(id).orElseThrow(() -> new IllegalArgumentException("审批流不存在：" + id));
    }

    /** 列出流程（可按业务类型过滤）。 */
    @Transactional(readOnly = true)
    public List<ApprovalFlow> list(ApprovalBizType bizType) {
        return bizType == null ? repo.findAllByOrderByIdDesc() : repo.findByBizTypeOrderByIdDesc(bizType);
    }

    /** 校验某流程的画布结构（不发布）。校验失败抛 FlowValidationException(400)。 */
    @Transactional(readOnly = true)
    public void validate(Long id) {
        validator.validate(parse(get(id).getGraphJson()));
    }

    /** 停用。 */
    @Transactional
    public ApprovalFlow retire(Long id) {
        ApprovalFlow flow = get(id);
        flow.retire();
        return repo.save(flow);
    }

    // ---------- 内部辅助 ----------

    /** 反序列化 graph_json → FlowGraph（供校验器/编译器使用）。 */
    public FlowGraph parse(String graphJson) {
        if (graphJson == null || graphJson.isBlank()) {
            throw new FlowValidationException("审批流为空，无法校验/发布");
        }
        try {
            return objectMapper.readValue(graphJson, FlowGraph.class);
        } catch (Exception e) {
            throw new FlowValidationException("审批流 JSON 解析失败：" + e.getMessage());
        }
    }

    private String toJson(FlowGraph graph) {
        try {
            return objectMapper.writeValueAsString(graph == null ? new FlowGraph(List.of(), List.of()) : graph);
        } catch (Exception e) {
            throw new FlowValidationException("审批流序列化失败：" + e.getMessage());
        }
    }
}
