package com.mandao.grc.modules.regulatory;

/**
 * 重大事件报送状态机（M11 监管事项）。
 *
 * 合法流转（七轮 7-2/B3 补 ACKNOWLEDGED 段，对齐 D1-4:191）：
 *   DRAFT --report--> REPORTED --acknowledge--> ACKNOWLEDGED --close--> CLOSED（终态）。
 * close 另受回执证据门控约束（证据库须挂有本事件的报送回执/确认材料）。
 * 非法流转由 {@link MajorIncidentService} 校验并抛 {@link IllegalStateException}。
 *
 * 设计依据：需求文档 M11 监管事项（重大事件报送）、D1-2 §23、D1-4:191、D2-5。
 */
public enum MajorIncidentStatus {

    /** 草稿：事件已录入，尚未上报监管。 */
    DRAFT,

    /** 已上报：已向监管机构报送。 */
    REPORTED,

    /** 监管已确认：监管机构确认收到报送（记录 acknowledged_at）。 */
    ACKNOWLEDGED,

    /** 已了结：事件闭环（终态，须挂回执证据）。 */
    CLOSED
}
