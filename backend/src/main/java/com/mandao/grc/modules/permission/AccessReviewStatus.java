package com.mandao.grc.modules.permission;

/**
 * UAR 权限审阅状态机枚举（M8）。
 *
 * 合法流转（其余一律非法，由 {@link AccessReviewService} 校验并抛 {@link IllegalStateException}）：
 *   OPEN --start--> IN_REVIEW
 *   IN_REVIEW --complete--> COMPLETED（终态）
 *
 * 仅 IN_REVIEW 态可对逐项做 {@link AccessReviewDecision} 决定（KEEP/REVOKE）。
 *
 * 设计依据：需求文档 M8 权限审批（UAR）、D1-3 §4.7、D2-5。
 */
public enum AccessReviewStatus {

    /** 已创建，尚未开始审阅。 */
    OPEN,

    /** 审阅中：可逐项做保留/撤销决定。 */
    IN_REVIEW,

    /** 已完成：终态。 */
    COMPLETED
}
