package com.mandao.grc.modules.policy;

/**
 * 制度状态机枚举。
 *
 * 合法流转（其余一律非法，由 {@link PolicyService} 校验并抛异常）：
 *   DRAFT --submit--> REVIEW
 *   REVIEW --approve--> EFFECTIVE
 *   REVIEW --reject--> DRAFT
 *   EFFECTIVE --archive--> DEPRECATED
 *
 * 即：DRAFT → REVIEW → EFFECTIVE → DEPRECATED（草稿→评审→生效→废止），REVIEW 可驳回回 DRAFT。
 *
 * 设计依据：D1-2 制度生命周期、D2-5 编码规范。
 */
public enum PolicyStatus {

    /** 草稿：可编辑、可提交评审。 */
    DRAFT,

    /** 评审：等待审批人批准或驳回。 */
    REVIEW,

    /** 生效：已发布生效，可被相关人员签署确认。 */
    EFFECTIVE,

    /** 废止：终态，不再生效。 */
    DEPRECATED
}
