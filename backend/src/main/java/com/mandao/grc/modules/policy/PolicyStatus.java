package com.mandao.grc.modules.policy;

/**
 * 制度状态机枚举。
 *
 * 合法流转（其余一律非法，由 {@link PolicyService} 校验并抛异常）：
 *   DRAFT --submit--> PENDING_APPROVAL
 *   PENDING_APPROVAL --approve--> PUBLISHED
 *   PENDING_APPROVAL --reject--> DRAFT
 *   PUBLISHED --archive--> ARCHIVED
 *
 * 设计依据：D1-2 制度生命周期、D2-5 编码规范。
 */
public enum PolicyStatus {

    /** 草稿：可编辑、可提交审批。 */
    DRAFT,

    /** 待审批：等待审批人批准或驳回。 */
    PENDING_APPROVAL,

    /** 已发布：生效，可被相关人员签署确认。 */
    PUBLISHED,

    /** 已归档：终态，不再生效。 */
    ARCHIVED
}
