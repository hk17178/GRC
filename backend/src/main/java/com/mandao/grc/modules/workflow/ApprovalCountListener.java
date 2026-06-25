package com.mandao.grc.modules.workflow;

import org.flowable.engine.delegate.TaskListener;
import org.flowable.task.service.delegate.DelegateTask;
import org.springframework.stereotype.Component;

/**
 * 审批计数监听器（会签/或签的核心机制）。
 *
 * 在每个审批任务"完成"时触发：按任务完成变量 decision(APPROVE/REJECT) 给「该节点」的计数器累加。
 * 计数器以节点 key 命名（approveCount_{nodeKey} / rejectCount_{nodeKey}），存于流程实例根作用域——
 * 这样多实例(会签/或签)各实例对同一节点计数器累加、不同节点互不串扰、也无需处理 MI 局部变量作用域，
 * 完成条件与节点后的排他网关据这两个计数器判定"通过/驳回"。
 *
 * 同步任务(async-executor-activate=false)下各 complete() 串行执行，计数无并发竞争。
 * Bean 名 approvalCountListener，BPMN 以 flowable:delegateExpression=${approvalCountListener} 引用。
 */
@Component("approvalCountListener")
public class ApprovalCountListener implements TaskListener {

    @Override
    public void notify(DelegateTask delegateTask) {
        String nodeKey = delegateTask.getTaskDefinitionKey();
        Object d = delegateTask.getVariable("decision");
        String decision = d == null ? "" : d.toString();
        String varName = ("APPROVE".equals(decision) ? "approveCount_" : "rejectCount_") + nodeKey;
        Object cur = delegateTask.getVariable(varName);
        int v = (cur instanceof Number n) ? n.intValue() : 0;
        // 变量首次出现时 Flowable 在流程实例根作用域创建/更新，故按节点 key 命名即"每节点独立、进程级共享"
        delegateTask.setVariable(varName, v + 1);
    }
}
