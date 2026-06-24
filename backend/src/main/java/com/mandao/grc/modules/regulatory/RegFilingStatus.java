package com.mandao.grc.modules.regulatory;

/**
 * 报送日历生命周期状态机（M11 监管事项）。
 *
 * 合法流转（其余一律非法，由 {@link RegFilingService} 校验并抛 {@link IllegalStateException}）：
 *   TO_DRAFT --prepare--> DRAFTING
 *   DRAFTING --submit--> SUBMITTED
 *   SUBMITTED --close--> CLOSED（终态）
 *
 * 注：本状态机为报送业务生命周期，与调度内核无关——调度据 statutory_deadline + reminder_days 产
 * REG_FILING_DUE（法定时限预警），不依赖此 status 列。
 *
 * 设计依据：需求文档 M11 监管事项（报送日历）、D1-2 §23、D2-5、DM-5 状态机基线。
 */
public enum RegFilingStatus {

    /** 待起草：报送事项已立项，尚未开始编制材料（默认初态）。 */
    TO_DRAFT,

    /** 起草中：正在编制报送材料。 */
    DRAFTING,

    /** 已报送：材料已提交监管机构。 */
    SUBMITTED,

    /** 已了结：报送闭环（终态）。 */
    CLOSED
}
