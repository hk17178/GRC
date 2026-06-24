package com.mandao.grc.modules.audit.management;

/**
 * 审计类型（M3 审计管理）。
 *
 * 决定审计计划/发现适用的处置路径：唯 {@link #EXTERNAL}（外部审计）的发现可走"对外回函三段漏斗"
 * （external_response_status：SUBMITTED→ACCEPTED→CONFIRMED_CLOSED），非外审调用漏斗一律被
 * {@link AuditFindingService} 拒绝（AuditFunnelException）。
 *
 * 注：与 V3 调度内核兼容——audit_plan.audit_type 默认 EXTERNAL，ExpiryScanService 不依赖此列，
 * 仍按 external_status='PLANNED' 扫描产 EXT_AUDIT_PLAN_APPROACHING。
 *
 * 设计依据：需求文档 M3 审计管理（内审/外审/监管检查/认证审计）。
 */
public enum AuditType {

    /** 内部审计。 */
    INTERNAL,

    /** 外部审计：唯一可走"对外回函三段漏斗"红线的类型。 */
    EXTERNAL,

    /** 监管检查。 */
    REGULATORY,

    /** 认证审计。 */
    CERTIFICATION
}
