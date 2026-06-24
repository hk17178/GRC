package com.mandao.grc.modules.assessment;

/**
 * 平台统一五级风险等级。固有风险与残余风险均取此枚举。
 *
 * 关闭门控（CR-002 红线）以残余等级判定：{@link #HIGH} 与 {@link #VERY_HIGH}
 * 为"高残余"，关闭前必须具备有效风险接受（risk_acceptance）。
 *
 * 设计依据：D1-2 数据模型（五级风险、关闭门控）。
 */
public enum RiskLevel {

    VERY_LOW,
    LOW,
    MID,
    HIGH,
    VERY_HIGH;

    /** 是否为"高残余"等级（HIGH / VERY_HIGH）——关闭门控的判定开关。 */
    public boolean isHighResidual() {
        return this == HIGH || this == VERY_HIGH;
    }
}
