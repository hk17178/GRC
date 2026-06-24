package com.mandao.grc.modules.audit.management;

/**
 * 审计发现严重度（M3 审计管理）五级——与平台 {@code RiskLevel} 同口径。
 *
 * VERY_LOW / LOW / MID / HIGH / VERY_HIGH（原 CRITICAL 概念上对应 VERY_HIGH）。
 *
 * 设计依据：需求文档 M3 审计管理（审计发现严重度，对齐平台五级风险口径）、D2-5 编码规范。
 */
public enum AuditSeverity {

    VERY_LOW,
    LOW,
    MID,
    HIGH,
    VERY_HIGH
}
