package com.mandao.grc.modules.regulatory;

/**
 * 年度合规计划项状态：PENDING → IN_PROGRESS → DONE。
 */
public enum CompliancePlanItemStatus {
    /** 待启动。 */
    PENDING,
    /** 推进中。 */
    IN_PROGRESS,
    /** 已完成。 */
    DONE
}
