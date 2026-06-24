package com.mandao.grc.modules.regulation;

/**
 * 法规变更的影响评估状态：PENDING → ASSESSED。
 *
 * 影响分析闭环（红线）：每条法规变更须完成影响评估（记录受影响范围与处置）方算闭环；
 * 未评估的变更滞留在 PENDING，构成法规跟踪工作清单。
 */
public enum ImpactStatus {
    /** 待评估。 */
    PENDING,
    /** 已评估（含受影响范围与处置）。 */
    ASSESSED
}
