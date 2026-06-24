package com.mandao.grc.modules.permission;

/**
 * UAR 逐项审阅决定（M8）。
 *
 *   PENDING：待决（建项默认）；
 *   KEEP：保留该授权（不改变 user_role_org.active）；
 *   REVOKE：撤销该授权——由 {@link AccessReviewService#decideItem} 将对应 {@link UserRoleOrg} 置 active=false；
 *   DOWNGRADE：降权（D1-3 三态之一）——本切片落地同样置对应 {@link UserRoleOrg} active=false，
 *     但以 decision=DOWNGRADE 与 REVOKE 区分语义（留痕 action=ACCESS_REVIEW_DOWNGRADE）。
 *
 * 设计依据：需求文档 M8 权限审批（UAR）、D1-3 §4.7（KEEP/REVOKE/DOWNGRADE 三态）。
 */
public enum AccessReviewDecision {

    /** 待决。 */
    PENDING,

    /** 保留。 */
    KEEP,

    /** 撤销（联动 user_role_org.active=false）。 */
    REVOKE,

    /** 降权（联动 user_role_org.active=false，以 decision 值与 REVOKE 区分语义）。 */
    DOWNGRADE
}
