package com.mandao.grc.modules.regulatory;

/**
 * 报送日历生命周期状态机（M11 监管事项）。
 *
 * 合法流转（其余一律非法，由 {@link RegFilingService} 校验并抛 {@link IllegalStateException}）：
 *   PLANNED --prepare--> PREPARING
 *   PREPARING --submit--> SUBMITTED
 *   SUBMITTED --accept--> ACCEPTED（终态）
 *
 * 注：本状态机为报送业务生命周期，与调度内核无关——调度据 statutory_deadline + reminder_days 产
 * REG_FILING_DUE（法定时限预警），不依赖此 status 列。
 *
 * 设计依据：需求文档 M11 监管事项（报送日历）、D1-2 §23、D2-5。
 */
public enum RegFilingStatus {

    /** 已计划：报送事项已立项，尚未开始准备材料。 */
    PLANNED,

    /** 准备中：正在编制报送材料。 */
    PREPARING,

    /** 已报送：材料已提交监管机构。 */
    SUBMITTED,

    /** 已受理：监管机构已受理（终态）。 */
    ACCEPTED
}
