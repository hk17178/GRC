package com.mandao.grc.modules.assessment;

/**
 * 风险发现状态机枚举。
 *
 * 合法流转（其余一律非法，由 {@link RiskFindingService} 校验并抛异常）：
 *   OPEN --setTreatment--> TREATING
 *   OPEN / TREATING --close--> DONE
 *   DONE --close(verify)--> VERIFIED
 *
 * 关闭门控（CR-002 红线）：当残余等级为 HIGH / VERY_HIGH 且无有效风险接受时，
 * 禁止流转到 DONE / VERIFIED（由 {@link RiskFindingService} 抛业务异常拦截）。
 *
 * 设计依据：D1-2 数据模型（风险发现生命周期、关闭门控）、D2-5 编码规范。
 */
public enum RiskFindingStatus {

    /** 待处置：风险已识别，尚未制定/执行处置。 */
    OPEN,

    /** 处置中：已制定处置方案并执行。 */
    TREATING,

    /** 已处置：处置完成（关闭，受残余风险门控约束）。 */
    DONE,

    /** 已验证：复核验证通过的终态（受残余风险门控约束）。 */
    VERIFIED
}
