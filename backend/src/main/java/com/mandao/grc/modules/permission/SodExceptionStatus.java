package com.mandao.grc.modules.permission;

/**
 * SoD 豁免审批状态。
 *
 * 申请后为 PENDING（不放行）；审批通过 APPROVED 才作为有效豁免，使 BLOCK 互斥授权放行；
 * 驳回 REJECTED 不放行。即「SoD 豁免须经审批通过」，而非自助登记即生效（职责分离红线放行侧）。
 */
public enum SodExceptionStatus {
    /** 待审批：已申请、审批中，尚未生效。 */
    PENDING,
    /** 已通过：作为有效豁免放行互斥授权。 */
    APPROVED,
    /** 已驳回：不予豁免，不放行。 */
    REJECTED
}
