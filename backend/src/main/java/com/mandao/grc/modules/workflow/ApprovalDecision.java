package com.mandao.grc.modules.workflow;

/**
 * 审批结论（通用审批流 genericApproval 的处置结果）。
 *
 * 以流程变量 decision 持久化其名（APPROVED/REJECTED）；业务模块在流程结束后据此推进自身状态机。
 */
public enum ApprovalDecision {
    /** 通过。 */
    APPROVED,
    /** 驳回。 */
    REJECTED
}
