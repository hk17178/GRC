package com.mandao.grc.modules.asset;

/**
 * 资产生命周期状态机（M6 资产台账）。
 *
 * 合法流转：ACTIVE --retire--> RETIRED（停用，终态）。
 * 非法流转由 {@link AssetService} 校验并抛 {@link IllegalStateException}。
 *
 * 设计依据：需求文档 M6 组织与资产、D1-2、D2-5。
 */
public enum AssetStatus {

    /** 在用：资产处于正常使用状态。 */
    ACTIVE,

    /** 已停用：资产退役/下线（终态）。 */
    RETIRED
}
