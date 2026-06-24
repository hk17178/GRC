package com.mandao.grc.modules.regulatory;

/**
 * 处罚约谈台账处置状态机（M11 监管事项）。
 *
 * 合法流转：OPEN --rectify--> RECTIFYING --close--> CLOSED（终态）。
 * 非法流转由 {@link RegPenaltyService} 校验并抛 {@link IllegalStateException}。
 *
 * 设计依据：需求文档 M11 监管事项（处罚约谈）、D1-2 §23、D2-5。
 */
public enum RegPenaltyStatus {

    /** 已登记：处罚/约谈已记录，尚未着手整改。 */
    OPEN,

    /** 整改中：正在按处罚/约谈要求整改。 */
    RECTIFYING,

    /** 已了结：整改完成并闭环（终态）。 */
    CLOSED
}
