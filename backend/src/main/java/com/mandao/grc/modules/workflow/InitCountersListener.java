package com.mandao.grc.modules.workflow;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.ExecutionListener;
import org.flowable.common.engine.api.delegate.Expression;

/**
 * 流程启动时初始化各审批节点计数器（approveCount_/rejectCount_ 置 0）。
 *
 * 必要性：Flowable JUEL 对"未定义的标识符"会抛 PropertyNotFoundException（而非解析为 null），
 * 故会签/或签的完成条件引用计数器前，计数器变量必须已存在。挂在 startEvent 上预置为 0，
 * 使 {@link ApprovalCountListener} 后续累加、完成条件正常求值。审批节点 key 列表由 BPMN 字段注入（逗号分隔）。
 */
public class InitCountersListener implements ExecutionListener {

    /** BPMN 字段注入：逗号分隔的审批节点 key。 */
    private Expression keys;

    @Override
    public void notify(DelegateExecution execution) {
        String joined = keys == null ? "" : String.valueOf(keys.getValue(execution));
        for (String key : joined.split(",")) {
            String k = key.trim();
            if (!k.isEmpty()) {
                execution.setVariable("approveCount_" + k, 0);
                execution.setVariable("rejectCount_" + k, 0);
            }
        }
    }

    public void setKeys(Expression keys) {
        this.keys = keys;
    }
}
