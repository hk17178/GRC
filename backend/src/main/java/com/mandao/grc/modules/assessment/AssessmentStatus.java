package com.mandao.grc.modules.assessment;

/**
 * 风险评估生命周期状态机枚举。
 *
 * 合法流转（其余一律非法，由 {@link AssessmentService} 校验并抛异常）：
 *   DRAFT --start--> IN_PROGRESS
 *   IN_PROGRESS --submit--> PENDING_REVIEW
 *   PENDING_REVIEW --complete--> COMPLETED
 *   PENDING_REVIEW --reject--> IN_PROGRESS（退回继续评估）
 *
 * 设计依据：D1-2 数据模型（评估生命周期）、D2-5 编码规范。
 */
public enum AssessmentStatus {

    /** 草稿：评估已建，尚未开始。 */
    DRAFT,

    /** 评估中：识别风险、录入风险发现与处置方案。 */
    IN_PROGRESS,

    /** 待复核：评估内容已提交，等待复核确认。 */
    PENDING_REVIEW,

    /** 已完成：终态。 */
    COMPLETED
}
