package com.mandao.grc.modules.audit.management;

/**
 * 审计发现处置状态机枚举（M3 审计管理）。
 *
 * 合法流转（其余一律非法，由 {@link AuditFindingService} 校验并抛 {@link IllegalStateException}）：
 *   OPEN --analyze--> ANALYZING
 *   ANALYZING --remediate--> REMEDIATED
 *   REMEDIATED --close--> CLOSED（终态）
 *
 * 注：此为发现的【内部处置】状态机，与"对外回函三段漏斗"（external_response_status）是两条独立轨：
 * 漏斗仅外审用、承载对外机构往返；本 status 承载内部处置进度。二者由 {@link AuditFindingService} 分别推进。
 *
 * 设计依据：需求文档 M3 审计管理（审计发现生命周期）、D2-5 编码规范。
 */
public enum AuditFindingStatus {

    /** 待分析：审计发现已记录，尚未分析。 */
    OPEN,

    /** 分析中：原因分析、影响评估进行中。 */
    ANALYZING,

    /** 已整改：整改措施完成。 */
    REMEDIATED,

    /** 已关闭：终态。 */
    CLOSED
}
