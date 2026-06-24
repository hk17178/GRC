package com.mandao.grc.modules.regulatory;

/**
 * 年度合规计划状态机：DRAFT → ACTIVE → CLOSED。
 * 仅 DRAFT 可增改计划项；ACTIVE 为执行中（计划项逐条推进）；CLOSED 为收口（终态）。
 */
public enum CompliancePlanStatus {
    /** 草稿：编制中，可增改计划项，未下发执行。 */
    DRAFT,
    /** 执行中：已下发，计划项逐条推进。 */
    ACTIVE,
    /** 已收口：年度计划终态。 */
    CLOSED
}
