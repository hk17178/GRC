package com.mandao.grc.modules.assessment;

/**
 * 风险评估生命周期状态机枚举。
 *
 * 合法流转（其余一律非法，由 {@link AssessmentService} 校验并抛异常）：
 *   DRAFT --start--> IN_PROGRESS
 *   IN_PROGRESS --submit--> PENDING_REVIEW
 *   PENDING_REVIEW --complete--> COMPLETED
 *   PENDING_REVIEW --reject--> IN_PROGRESS（退回继续评估）
 *   IN_PROGRESS / PENDING_REVIEW --cancel--> CANCELLED（作废终态，UAT 五轮 #1）
 *
 * 删除口径（UAT 五轮 #1）：DRAFT 可物理删除（无留痕价值，级联清理）；
 * 进行中/待复核只可作废（软删，审计证据不物理销毁）；COMPLETED 已定稿冻结不可动。
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
    COMPLETED,

    /** 已作废：终态（软删——审计证据不物理销毁，列表默认隐藏）。 */
    CANCELLED
}
