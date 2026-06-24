package com.mandao.grc.modules.assessment;

/**
 * 评估项结论（对单个控制点的符合性判定）。
 */
public enum AssessmentItemResult {
    /** 待评：尚未给出结论。 */
    PENDING,
    /** 符合。 */
    CONFORMING,
    /** 不符合（通常据此派生整改/风险发现）。 */
    NONCONFORMING,
    /** 不适用。 */
    NOT_APPLICABLE
}
