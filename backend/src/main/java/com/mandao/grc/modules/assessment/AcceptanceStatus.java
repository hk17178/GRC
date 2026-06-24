package com.mandao.grc.modules.assessment;

/**
 * 风险接受审批状态（CR-002 关闭门控放行凭据的生命周期）。
 *
 * 申请后为 PENDING（不放行）；审批通过 APPROVED 时才回填 finding.risk_acceptance_id（门控解除）；
 * 驳回 REJECTED 不放行。即「高残余关闭须经审批通过的风险接受」，而非自助登记即放行。
 */
public enum AcceptanceStatus {
    /** 待审批：已提交申请、审批中，尚未放行。 */
    PENDING,
    /** 已通过：审批通过，作为放行凭据回填到风险发现。 */
    APPROVED,
    /** 已驳回：不予接受，不放行。 */
    REJECTED
}
