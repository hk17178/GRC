package com.mandao.grc.modules.ropa;

/**
 * 个人信息处理活动（ROPA）生命周期状态机（M6）。
 *
 * 合法流转：DRAFT --activate--> ACTIVE --retire--> RETIRED（终态）。
 * 非法流转由 {@link RopaService} 校验并抛 {@link IllegalStateException}。
 *
 * 设计依据：需求文档 M6 组织与资产（个人信息处理活动 ROPA）、D1-2、D2-5。
 */
public enum RopaStatus {

    /** 草稿：处理活动登记中，尚未生效。 */
    DRAFT,

    /** 生效：处理活动已确认并在运行。 */
    ACTIVE,

    /** 已退役：处理活动停止（终态）。 */
    RETIRED
}
