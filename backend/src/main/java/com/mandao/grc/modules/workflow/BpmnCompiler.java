package com.mandao.grc.modules.workflow;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 画布图 → Flowable BPMN 2.0 编译器。
 *
 * 把 {@link FlowGraph} 编译成可部署执行的 BPMN：
 *  - 审批节点：单人=普通用户任务 + 决定网关；多人=多实例(并行)用户任务 + 计数监听({@link ApprovalCountListener})，
 *    会签 ALL(全通过)/或签 ANY(任 N 通过) 由完成条件 + 节点后排他网关据 approveCount/rejectCount 判定；
 *  - 驳回为隐式：任一审批节点不通过 → 直达「驳回结束」(terminate)，结论 REJECTED；通过链走到「通过结束」结论 APPROVED；
 *  - 表达式用 JUEL 关键字算子(and/or/ge/gt/eq)规避 XML 转义；计数器按节点 key 命名、进程级共享、各节点独立。
 *
 * 本版(P1.2a)支持：开始 / 审批(单·会签·或签) / 结束 + 隐式驳回。并行/条件/超时升级在后续增量接入
 * （遇到这些节点类型抛 {@link FlowValidationException} 明示尚未支持，绝不静默产出错误流程）。
 */
@Component
public class BpmnCompiler {

    /** 编译产物：流程 key + BPMN XML。 */
    public record Compiled(String processKey, String bpmnXml) {
    }

    public Compiled compile(ApprovalFlow flow, FlowGraph g) {
        String pkey = "approvalFlow" + flow.getId();
        Map<String, FlowGraph.FlowNode> byKey = new HashMap<>();
        String startKey = null;
        for (FlowGraph.FlowNode n : g.nodes()) {
            byKey.put(n.key(), n);
            if (n.type() == NodeType.START) {
                startKey = n.key();
            }
        }
        // 每节点出边列表（条件/并行分叉有多条；审批/合流/开始恰一条）
        Map<String, List<FlowGraph.FlowEdge>> outs = new HashMap<>();
        for (FlowGraph.FlowEdge e : (g.edges() == null ? List.<FlowGraph.FlowEdge>of() : g.edges())) {
            outs.computeIfAbsent(e.from(), k -> new ArrayList<>()).add(e);
        }
        String firstNode = (startKey != null && outs.containsKey(startKey)) ? outs.get(startKey).get(0).to() : null;

        StringBuilder sb = new StringBuilder(1024);
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" ")
                .append("xmlns:flowable=\"http://flowable.org/bpmn\" ")
                .append("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" ")
                .append("targetNamespace=\"http://mandao.com/grc/approval\">\n");
        sb.append("  <process id=\"").append(pkey).append("\" name=\"").append(esc(flow.getName())).append("\" isExecutable=\"true\">\n");

        boolean rejectUsed = false;
        // 启动监听器：预置各审批节点计数器为 0（规避 JUEL 未定义标识符异常）
        String approvalKeys = g.nodes().stream()
                .filter(n -> n.type() == NodeType.APPROVAL)
                .map(FlowGraph.FlowNode::key)
                .reduce((a, b) -> a + "," + b).orElse("");
        sb.append("    <startEvent id=\"start\">\n")
          .append("      <extensionElements>\n")
          .append("        <flowable:executionListener event=\"start\" class=\"com.mandao.grc.modules.workflow.InitCountersListener\">\n")
          .append("          <flowable:field name=\"keys\"><flowable:string>").append(approvalKeys).append("</flowable:string></flowable:field>\n")
          .append("        </flowable:executionListener>\n")
          .append("      </extensionElements>\n")
          .append("    </startEvent>\n");
        if (firstNode != null) {
            sb.append(flowXml("start_flow", "start", firstNode, null));
        }

        for (FlowGraph.FlowNode n : g.nodes()) {
            switch (n.type()) {
                case START -> { /* 已发出 */ }
                case END -> sb.append(endXml(n.key(), "APPROVED".equals(n.outcome()) ? "APPROVED" : "REJECTED", false));
                case APPROVAL -> {
                    rejectUsed = true;
                    appendApproval(sb, n, outs.get(n.key()).get(0).to());
                }
                case PARALLEL_SPLIT -> {
                    sb.append("    <parallelGateway id=\"").append(n.key()).append("\"/>\n");
                    int i = 0;
                    for (FlowGraph.FlowEdge e : outs.getOrDefault(n.key(), List.of())) {
                        sb.append(flowXml(n.key() + "_o" + (i++), n.key(), e.to(), null));
                    }
                }
                case PARALLEL_JOIN -> {
                    sb.append("    <parallelGateway id=\"").append(n.key()).append("\"/>\n");
                    sb.append(flowXml(n.key() + "_o", n.key(), outs.get(n.key()).get(0).to(), null));
                }
                case CONDITION -> {
                    String defId = n.key() + "_def";
                    sb.append("    <exclusiveGateway id=\"").append(n.key()).append("\" default=\"").append(defId).append("\"/>\n");
                    int i = 0;
                    for (FlowGraph.FlowEdge e : outs.getOrDefault(n.key(), List.of())) {
                        boolean isDefault = (e.condition() == null || e.condition().isBlank());
                        String fid = isDefault ? defId : n.key() + "_c" + (i++);
                        sb.append(flowXml(fid, n.key(), e.to(), isDefault ? null : "${" + e.condition() + "}"));
                    }
                }
            }
        }

        if (rejectUsed) {
            sb.append(endXml("end_rejected", "REJECTED", true));
        }
        sb.append("  </process>\n</definitions>\n");
        return new Compiled(pkey, sb.toString());
    }

    /** 审批节点：单人=普通任务；多人=多实例任务 + 计数；节点后排他网关 通过→下一节点 / 默认→驳回结束。 */
    private void appendApproval(StringBuilder sb, FlowGraph.FlowNode n, String pass) {
        String key = n.key();
        int total = n.approverRefs().size();
        boolean multi = total > 1;
        String assignAttr = assignAttr(n.approverType(), multi ? "${approverRef}" : String.join(",", n.approverRefs()));

        sb.append("    <userTask id=\"").append(key).append("\" name=\"").append(esc(n.name())).append("\" ").append(assignAttr).append(">\n");
        if (multi) {
            sb.append("      <extensionElements>\n")
              .append("        <flowable:taskListener event=\"complete\" delegateExpression=\"${approvalCountListener}\"/>\n")
              .append("      </extensionElements>\n");
            String completion = (n.mode() == CountersignMode.ALL)
                    ? "${" + rj(key) + " gt 0 or " + ap(key) + " ge " + total + "}"
                    : "${" + ap(key) + " ge " + req(n) + " or (" + ap(key) + " + " + rj(key) + ") ge " + total + "}";
            sb.append("      <multiInstanceLoopCharacteristics isSequential=\"false\" ")
              .append("flowable:collection=\"${approvers_").append(key).append("}\" flowable:elementVariable=\"approverRef\">\n")
              .append("        <completionCondition>").append(completion).append("</completionCondition>\n")
              .append("      </multiInstanceLoopCharacteristics>\n");
        }
        sb.append("    </userTask>\n");

        // 通过判定网关（默认边=驳回）
        String gw = key + "_gw";
        sb.append("    <exclusiveGateway id=\"").append(gw).append("\" default=\"").append(key).append("_fail\"/>\n");
        sb.append(flowXml(key + "_to_gw", key, gw, null));
        String passCond = multi
                ? "${" + ap(key) + " ge " + (n.mode() == CountersignMode.ALL ? total : req(n)) + "}"
                : "${decision eq 'APPROVE'}";
        sb.append(flowXml(key + "_pass", gw, pass, passCond));
        sb.append(flowXml(key + "_fail", gw, "end_rejected", null));

        // 超时自动升级：中断型边界定时器 → 升级审批任务(改派升级目标) → 独立决定网关
        if (n.timeoutHours() != null && n.timeoutHours() > 0
                && n.escalateTo() != null && n.escalateTo().ref() != null && !n.escalateTo().ref().isBlank()) {
            String esc = key + "_esc";
            String escgw = key + "_escgw";
            sb.append("    <boundaryEvent id=\"").append(key).append("_to\" attachedToRef=\"").append(key).append("\" cancelActivity=\"true\">\n")
              .append("      <timerEventDefinition><timeDuration>PT").append(n.timeoutHours()).append("H</timeDuration></timerEventDefinition>\n")
              .append("    </boundaryEvent>\n");
            sb.append("    <userTask id=\"").append(esc).append("\" name=\"").append(esc(n.name())).append("·超时升级\" ")
              .append(assignAttr(n.escalateTo().type(), n.escalateTo().ref())).append("/>\n");
            sb.append("    <exclusiveGateway id=\"").append(escgw).append("\" default=\"").append(key).append("_escfail\"/>\n");
            sb.append(flowXml(key + "_to_esc", key + "_to", esc, null));
            sb.append(flowXml(key + "_esc_gw", esc, escgw, null));
            sb.append(flowXml(key + "_escpass", escgw, pass, "${decision eq 'APPROVE'}"));
            sb.append(flowXml(key + "_escfail", escgw, "end_rejected", null));
        }
    }

    /** 结束事件（带结论回写监听；rejected 额外 terminate）。 */
    private String endXml(String id, String outcome, boolean terminate) {
        StringBuilder s = new StringBuilder();
        s.append("    <endEvent id=\"").append(id).append("\">\n")
         .append("      <extensionElements>\n")
         .append("        <flowable:executionListener event=\"start\" class=\"com.mandao.grc.modules.workflow.SetOutcomeListener\">\n")
         .append("          <flowable:field name=\"outcome\"><flowable:string>").append(outcome).append("</flowable:string></flowable:field>\n")
         .append("        </flowable:executionListener>\n")
         .append("      </extensionElements>\n");
        if (terminate) {
            s.append("      <terminateEventDefinition/>\n");
        }
        s.append("    </endEvent>\n");
        return s.toString();
    }

    private String flowXml(String id, String from, String to, String cond) {
        if (cond == null) {
            return "    <sequenceFlow id=\"" + id + "\" sourceRef=\"" + from + "\" targetRef=\"" + to + "\"/>\n";
        }
        // 条件表达式做 XML 转义；Flowable 解析时会还原实体，JUEL 求值正常（兼容用户条件中的 < > &）
        return "    <sequenceFlow id=\"" + id + "\" sourceRef=\"" + from + "\" targetRef=\"" + to + "\">\n"
                + "      <conditionExpression xsi:type=\"tFormalExpression\">" + esc(cond) + "</conditionExpression>\n"
                + "    </sequenceFlow>\n";
    }

    private String assignAttr(ApproverType type, String value) {
        return type == ApproverType.USER
                ? "flowable:assignee=\"" + value + "\""
                : "flowable:candidateGroups=\"" + value + "\"";
    }

    /** 或签通过人数（缺省 1）。 */
    private int req(FlowGraph.FlowNode n) {
        return (n.requiredCount() == null || n.requiredCount() < 1) ? 1 : n.requiredCount();
    }

    /** 计数器 JUEL 片段（null 安全）。 */
    private String ap(String key) {
        return "(approveCount_" + key + " == null ? 0 : approveCount_" + key + ")";
    }

    private String rj(String key) {
        return "(rejectCount_" + key + " == null ? 0 : rejectCount_" + key + ")";
    }

    private String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
