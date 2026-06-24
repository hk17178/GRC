package com.mandao.grc.modules.audit.management;

/**
 * 整改工单状态机：PENDING → IN_PROGRESS → SUBMITTED → VERIFIED。
 * SUBMITTED 验证不通过则退回 IN_PROGRESS（返工）。VERIFIED 为终态。
 *
 * 验证闭环（红线）：审计发现须有【至少一条 VERIFIED 工单】方可标记为已整改（见 AuditFindingService.remediate）。
 */
public enum RemediationStatus {
    /** 待办：已派单，未开始。 */
    PENDING,
    /** 整改中。 */
    IN_PROGRESS,
    /** 已提交：待验证。 */
    SUBMITTED,
    /** 已验证：整改通过（终态，闭环凭据）。 */
    VERIFIED
}
