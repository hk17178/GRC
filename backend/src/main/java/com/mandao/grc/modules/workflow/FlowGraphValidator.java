package com.mandao.grc.modules.workflow;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 审批流图结构/属性校验器（发布前把关，保证可编译为合法 BPMN）。
 *
 * 校验项：唯一 START、至少一个 END(outcome 合法)、节点 key 唯一、连线引用存在；
 * APPROVAL 必有审批人来源+至少一名审批人+会签/或签模式，或签人数 1..N，超时须配升级目标；
 * 并行分叉/合流须分别 ≥2 出/入边；条件节点须 ≥2 出边且含一条默认（无条件）边；
 * 可达性：每个节点须从 START 可达。问题聚合后抛 {@link FlowValidationException}。
 */
@Component
public class FlowGraphValidator {

    public void validate(FlowGraph g) {
        List<String> errs = new ArrayList<>();
        if (g == null || g.nodes() == null || g.nodes().isEmpty()) {
            throw new FlowValidationException("审批流为空：至少需要 开始→审批→结束 三个节点");
        }
        List<FlowGraph.FlowNode> nodes = g.nodes();
        List<FlowGraph.FlowEdge> edges = g.edges() == null ? List.of() : g.edges();

        // key 唯一 + 索引
        Map<String, FlowGraph.FlowNode> byKey = new HashMap<>();
        for (FlowGraph.FlowNode n : nodes) {
            if (n.key() == null || n.key().isBlank()) {
                errs.add("存在无 key 的节点");
                continue;
            }
            if (byKey.put(n.key(), n) != null) {
                errs.add("节点 key 重复：" + n.key());
            }
            if (n.type() == null) {
                errs.add("节点 " + n.key() + " 缺类型");
            }
        }

        // START / END 计数
        long starts = nodes.stream().filter(n -> n.type() == NodeType.START).count();
        long ends = nodes.stream().filter(n -> n.type() == NodeType.END).count();
        if (starts != 1) {
            errs.add("必须有且仅有一个 开始 节点（当前 " + starts + " 个）");
        }
        if (ends < 1) {
            errs.add("至少需要一个 结束 节点");
        }
        for (FlowGraph.FlowNode n : nodes) {
            if (n.type() == NodeType.END && !("APPROVED".equals(n.outcome()) || "REJECTED".equals(n.outcome()))) {
                errs.add("结束节点 " + n.key() + " 的 outcome 必须为 APPROVED 或 REJECTED");
            }
        }

        // 连线引用存在 + 出入度统计
        Map<String, Integer> outDeg = new HashMap<>();
        Map<String, Integer> inDeg = new HashMap<>();
        for (FlowGraph.FlowEdge e : edges) {
            if (!byKey.containsKey(e.from())) {
                errs.add("连线起点不存在：" + e.from());
            }
            if (!byKey.containsKey(e.to())) {
                errs.add("连线终点不存在：" + e.to());
            }
            outDeg.merge(e.from(), 1, Integer::sum);
            inDeg.merge(e.to(), 1, Integer::sum);
        }

        // 各节点类型的属性/结构校验
        for (FlowGraph.FlowNode n : nodes) {
            int out = outDeg.getOrDefault(n.key(), 0);
            int in = inDeg.getOrDefault(n.key(), 0);
            switch (n.type() == null ? NodeType.APPROVAL : n.type()) {
                case START -> {
                    if (in != 0) {
                        errs.add("开始节点不应有入边");
                    }
                    if (out != 1) {
                        errs.add("开始节点应恰有 1 条出边");
                    }
                }
                case END -> {
                    if (out != 0) {
                        errs.add("结束节点 " + n.key() + " 不应有出边");
                    }
                }
                case APPROVAL -> {
                    if (n.approverType() == null || n.approverRefs() == null || n.approverRefs().isEmpty()) {
                        errs.add("审批节点 " + nm(n) + " 须配置审批人来源与至少一名审批人");
                    }
                    if (n.mode() == null) {
                        errs.add("审批节点 " + nm(n) + " 须选择 会签(ALL) 或 或签(ANY)");
                    }
                    if (n.mode() == CountersignMode.ANY && n.requiredCount() != null
                            && n.approverRefs() != null
                            && (n.requiredCount() < 1 || n.requiredCount() > n.approverRefs().size())) {
                        errs.add("审批节点 " + nm(n) + " 或签通过人数须在 1.." + n.approverRefs().size());
                    }
                    if (n.timeoutHours() != null && n.timeoutHours() > 0
                            && (n.escalateTo() == null || n.escalateTo().ref() == null || n.escalateTo().ref().isBlank())) {
                        errs.add("审批节点 " + nm(n) + " 设了超时但未配升级目标");
                    }
                    if (out != 1) {
                        errs.add("审批节点 " + nm(n) + " 应恰有 1 条通过出边（驳回为隐式）");
                    }
                }
                case CONDITION -> {
                    if (out < 2) {
                        errs.add("条件节点 " + nm(n) + " 至少需 2 条分支出边");
                    }
                    long def = edges.stream().filter(e -> n.key().equals(e.from())
                            && (e.condition() == null || e.condition().isBlank())).count();
                    if (def != 1) {
                        errs.add("条件节点 " + nm(n) + " 须恰有 1 条默认（无条件）分支");
                    }
                }
                case PARALLEL_SPLIT -> {
                    if (out < 2) {
                        errs.add("并行分叉 " + nm(n) + " 至少需 2 条并行出边");
                    }
                }
                case PARALLEL_JOIN -> {
                    if (in < 2) {
                        errs.add("并行合流 " + nm(n) + " 至少需 2 条入边");
                    }
                }
            }
        }

        // 可达性：从 START 出发 BFS，所有节点须可达
        FlowGraph.FlowNode start = nodes.stream().filter(n -> n.type() == NodeType.START).findFirst().orElse(null);
        if (start != null && errs.isEmpty()) {
            Map<String, List<String>> adj = new HashMap<>();
            for (FlowGraph.FlowEdge e : edges) {
                adj.computeIfAbsent(e.from(), k -> new ArrayList<>()).add(e.to());
            }
            Set<String> seen = new HashSet<>();
            List<String> queue = new ArrayList<>();
            queue.add(start.key());
            seen.add(start.key());
            for (int i = 0; i < queue.size(); i++) {
                for (String nx : adj.getOrDefault(queue.get(i), List.of())) {
                    if (seen.add(nx)) {
                        queue.add(nx);
                    }
                }
            }
            for (FlowGraph.FlowNode n : nodes) {
                if (!seen.contains(n.key())) {
                    errs.add("节点 " + nm(n) + " 从开始不可达（有孤立节点或断线）");
                }
            }
        }

        if (!errs.isEmpty()) {
            throw new FlowValidationException("审批流校验未通过：\n- " + String.join("\n- ", errs));
        }
    }

    private String nm(FlowGraph.FlowNode n) {
        return (n.name() == null || n.name().isBlank()) ? n.key() : n.name() + "(" + n.key() + ")";
    }
}
