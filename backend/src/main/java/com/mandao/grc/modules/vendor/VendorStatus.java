package com.mandao.grc.modules.vendor;

/**
 * 供应商状态机：ONBOARDING → ACTIVE ⇄ SUSPENDED → TERMINATED。
 *
 * 准入门控（红线）：ONBOARDING → ACTIVE 须先完成至少一次风险评估（见 VendorService.activate）。
 */
public enum VendorStatus {
    /** 准入中：已登记，未通过评估启用。 */
    ONBOARDING,
    /** 合作中：已启用。 */
    ACTIVE,
    /** 已暂停：监测发现问题暂停合作（可恢复）。 */
    SUSPENDED,
    /** 已终止：终态。 */
    TERMINATED
}
