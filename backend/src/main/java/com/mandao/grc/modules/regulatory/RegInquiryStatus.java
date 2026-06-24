package com.mandao.grc.modules.regulatory;

/**
 * 监管问询处置状态机（M11 监管事项）。
 *
 * 合法流转：OPEN --respond--> RESPONDING --close--> CLOSED（终态）。
 * 非法流转由 {@link RegInquiryService} 校验并抛 {@link IllegalStateException}。
 *
 * 设计依据：需求文档 M11 监管事项（监管问询）、D1-2 §23、D2-5。
 */
public enum RegInquiryStatus {

    /** 已收到：问询已登记，尚未着手答复。 */
    OPEN,

    /** 答复中：正在准备/提交答复。 */
    RESPONDING,

    /** 已了结：问询闭环（终态）。 */
    CLOSED
}
