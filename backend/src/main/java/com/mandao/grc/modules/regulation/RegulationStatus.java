package com.mandao.grc.modules.regulation;

/**
 * 法规状态机（法规跟踪·法规库）：TRACKING → EFFECTIVE → SUPERSEDED/ABOLISHED。
 * TRACKING 拟生效/跟踪中；EFFECTIVE 现行有效；SUPERSEDED 已被新规取代；ABOLISHED 已废止（均为终态）。
 */
public enum RegulationStatus {
    /** 跟踪中（拟生效/征求意见）。 */
    TRACKING,
    /** 现行有效。 */
    EFFECTIVE,
    /** 已被取代（终态）。 */
    SUPERSEDED,
    /** 已废止（终态）。 */
    ABOLISHED
}
