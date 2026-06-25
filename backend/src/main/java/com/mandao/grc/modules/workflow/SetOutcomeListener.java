package com.mandao.grc.modules.workflow;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.ExecutionListener;
import org.flowable.common.engine.api.delegate.Expression;

/**
 * 流程结束时回写最终结论的执行监听器。
 *
 * 挂在「通过结束」与「驳回结束」事件上，把 approvalOutcome 置为 APPROVED / REJECTED（值经 BPMN 字段注入）。
 * 业务层据此变量推进自身状态机。class 委派 + 字段注入：每次新建实例、outcome 字段由 BPMN 的 flowable:field 注入。
 */
public class SetOutcomeListener implements ExecutionListener {

    /** BPMN 字段注入：APPROVED / REJECTED。 */
    private Expression outcome;

    @Override
    public void notify(DelegateExecution execution) {
        String value = outcome == null ? null : String.valueOf(outcome.getValue(execution));
        execution.setVariable("approvalOutcome", value);
    }

    public void setOutcome(Expression outcome) {
        this.outcome = outcome;
    }
}
