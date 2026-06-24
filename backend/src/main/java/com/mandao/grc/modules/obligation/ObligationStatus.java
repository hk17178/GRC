package com.mandao.grc.modules.obligation;

/**
 * 合规义务落实状态机：PENDING → IN_PROGRESS → FULFILLED；可标记 NON_COMPLIANT（不合规）后再整改回 IN_PROGRESS。
 *
 * 落实闭环（红线）：FULFILLED 须留证据（见 ObligationService.fulfill）。
 */
public enum ObligationStatus {
    /** 待落实。 */
    PENDING,
    /** 落实中。 */
    IN_PROGRESS,
    /** 已落实（须留证据）。 */
    FULFILLED,
    /** 不合规（落实缺失/不达标，待整改）。 */
    NON_COMPLIANT
}
