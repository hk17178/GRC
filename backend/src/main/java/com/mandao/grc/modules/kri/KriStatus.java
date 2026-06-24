package com.mandao.grc.modules.kri;

/**
 * KRI 健康状态（按最近一次测量值对阈值评定）。
 */
public enum KriStatus {
    /** 未知：尚无测量值。 */
    UNKNOWN,
    /** 正常：未触及预警阈值。 */
    NORMAL,
    /** 预警：触及 warning 阈值但未达 critical。 */
    WARNING,
    /** 严重：达到 critical 阈值（红线触发）。 */
    CRITICAL
}
