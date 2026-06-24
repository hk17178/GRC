package com.mandao.grc.modules.regulatory;

/**
 * 重大事件报送状态机（M11 监管事项）。
 *
 * 合法流转：DRAFT --report--> REPORTED --close--> CLOSED（终态）。
 * 非法流转由 {@link MajorIncidentService} 校验并抛 {@link IllegalStateException}。
 *
 * 设计依据：需求文档 M11 监管事项（重大事件报送）、D1-2 §23、D2-5。
 */
public enum MajorIncidentStatus {

    /** 草稿：事件已录入，尚未上报监管。 */
    DRAFT,

    /** 已上报：已向监管机构报送。 */
    REPORTED,

    /** 已了结：事件闭环（终态）。 */
    CLOSED
}
