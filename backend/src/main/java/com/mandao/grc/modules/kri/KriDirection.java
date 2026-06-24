package com.mandao.grc.modules.kri;

/**
 * KRI 阈值方向：决定"越界"的比较方向。
 *
 * - {@link #UPPER_BAD}：值越高越坏（如高危漏洞数、逾期事项数）——值 ≥ 阈值即越界；warning &lt; critical。
 * - {@link #LOWER_BAD}：值越低越坏（如补丁覆盖率、可用率）——值 ≤ 阈值即越界；warning &gt; critical。
 */
public enum KriDirection {
    /** 越高越坏（值 ≥ 阈值越界）。 */
    UPPER_BAD,
    /** 越低越坏（值 ≤ 阈值越界）。 */
    LOWER_BAD
}
