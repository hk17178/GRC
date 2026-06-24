package com.mandao.grc.modules.audit.management;

/**
 * 审计计划生命周期状态机枚举（M3 审计管理）。
 *
 * 合法流转（其余一律非法，由 {@link AuditPlanService} 校验并抛 {@link IllegalStateException}）：
 *   PLANNED --start--> IN_PROGRESS
 *   IN_PROGRESS --report--> REPORTED
 *   REPORTED --close--> CLOSED（终态）
 *
 * 注：本状态机独立于 V3 audit_plan.external_status（调度内核专用，仍由 ExpiryScanService 读取），
 * 两者互不影响——本 status 列为 M3 业务生命周期，external_status 仅作调度触发开关。
 *
 * 设计依据：需求文档 M3 审计管理（审计计划生命周期）、D2-5 编码规范。
 */
public enum AuditPlanStatus {

    /** 已计划：审计已立项，尚未开始执行。 */
    PLANNED,

    /** 执行中：审计正在实施，识别并录入审计发现。 */
    IN_PROGRESS,

    /** 已出报告：审计执行完毕、报告已出具。 */
    REPORTED,

    /** 已关闭：终态。 */
    CLOSED
}
