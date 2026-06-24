package com.mandao.grc.modules.feedback;

/**
 * 反馈处理状态机：SUBMITTED → IN_PROGRESS → RESOLVED → CLOSED；SUBMITTED/IN_PROGRESS 可 REJECTED。
 *
 * 办结闭环（红线）：RESOLVED 须填处置结果（见 FeedbackService.resolve）。
 */
public enum FeedbackStatus {
    /** 已提交：待受理。 */
    SUBMITTED,
    /** 处理中：已受理并分派处理人。 */
    IN_PROGRESS,
    /** 已办结：已给出处置结果。 */
    RESOLVED,
    /** 已关闭（终态）。 */
    CLOSED,
    /** 已驳回（终态，不予处理）。 */
    REJECTED
}
