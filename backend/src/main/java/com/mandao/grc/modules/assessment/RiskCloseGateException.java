package com.mandao.grc.modules.assessment;

/**
 * 残余风险关闭门控异常（CR-002 红线）。
 *
 * 当残余等级为高/极高（HIGH/VERY_HIGH）的风险发现，在无有效风险接受（risk_acceptance）的情况下
 * 试图关闭（流转到 DONE/VERIFIED）时抛出，阻断关闭。
 *
 * 单列为业务异常（而非泛化 IllegalStateException），便于上层精确识别门控拦截、做差异化处置与告警。
 */
public class RiskCloseGateException extends RuntimeException {

    public RiskCloseGateException(String message) {
        super(message);
    }
}
