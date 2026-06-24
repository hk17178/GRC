package com.mandao.grc.modules.audit.management;

/**
 * 审计发现严重度（M3 审计管理）四档。
 *
 * 设计依据：需求文档 M3 审计管理（审计发现严重度）、D2-5 编码规范。
 */
public enum AuditSeverity {

    LOW,
    MID,
    HIGH,
    CRITICAL
}
