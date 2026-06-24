package com.mandao.grc.modules.regulatory;

/**
 * 监管问询处置状态机（M11 监管事项）。
 *
 * 合法流转（其余一律非法，由 {@link RegInquiryService} 校验并抛 {@link IllegalStateException}）：
 *   DRAFTING --reply--> REPLIED --awaitFeedback--> AWAIT_FEEDBACK --close--> CLOSED（终态）。
 *
 * 设计依据：需求文档 M11 监管事项（监管问询）、D1-2 §23、D2-5、DM-5 状态机基线。
 */
public enum RegInquiryStatus {

    /** 答复起草中：问询已登记，正在起草答复（默认初态）。 */
    DRAFTING,

    /** 已答复：答复已提交监管机构。 */
    REPLIED,

    /** 待反馈：等待监管机构反馈/进一步意见。 */
    AWAIT_FEEDBACK,

    /** 已了结：问询闭环（终态）。 */
    CLOSED
}
