package com.mandao.grc.modules.workflow;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * 审批流图（可视化画布的数据契约 = approval_flow.graph_json）。
 *
 * 由前端 Vue Flow 画布生成、后端校验并编译成 BPMN。为前后端兼容，反序列化忽略未知字段。
 *
 * 结构约定（编译器与校验器据此）：
 *  - 必有唯一 START 与至少一个 END（END.outcome ∈ APPROVED/REJECTED）；
 *  - APPROVAL 节点：approverType + approverRefs（角色码/用户/组），mode(ANY/ALL)，
 *    requiredCount（ANY 时"任 N 人"，缺省 1；ALL 视为全部），timeoutHours+escalateTo（可空 = 不启用超时升级）；
 *  - CONDITION 节点：出边带 condition 表达式 + 一条默认边（condition 空）；
 *  - PARALLEL_SPLIT 多条出边并行，PARALLEL_JOIN 多条入边合流（须成对、结构化）；
 *  - 驳回为隐式：任一 APPROVAL 节点驳回 → 直达 REJECTED 结束（画布无需画驳回线）。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FlowGraph(List<FlowNode> nodes, List<FlowEdge> edges) {

    /** 节点（不同 type 用到不同字段；未用字段留 null）。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FlowNode(
            String key,
            NodeType type,
            String name,
            // APPROVAL 专用
            ApproverType approverType,
            List<String> approverRefs,
            CountersignMode mode,
            Integer requiredCount,
            Integer timeoutHours,
            Escalation escalateTo,
            // END 专用：APPROVED / REJECTED
            String outcome) {
    }

    /** 连线（CONDITION 出边携带 condition 表达式；其余边 condition 为空）。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FlowEdge(String from, String to, String condition) {
    }

    /** 超时自动升级目标（到点未审则改派给该角色/用户）。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Escalation(ApproverType type, String ref) {
    }
}
