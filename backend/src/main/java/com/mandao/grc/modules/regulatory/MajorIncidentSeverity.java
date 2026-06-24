package com.mandao.grc.modules.regulatory;

/**
 * 重大事件严重度（M11 监管事项）五级——与平台 {@code RiskLevel} / {@code AuditSeverity} 同口径。
 *
 * VERY_LOW / LOW / MID / HIGH / VERY_HIGH，统一平台五级严重度，替代原自由文本字段。
 *
 * 设计依据：需求文档 M11 监管事项（重大事件报送）、D1-2 §23、D2-5、DM-5 严重度对齐基线。
 */
public enum MajorIncidentSeverity {

    VERY_LOW,
    LOW,
    MID,
    HIGH,
    VERY_HIGH
}
