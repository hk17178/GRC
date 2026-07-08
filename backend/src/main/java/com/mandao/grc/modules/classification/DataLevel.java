package com.mandao.grc.modules.classification;

/**
 * 数据密级（B30 数据分级引擎），三级由低到高：PUBLIC → INTERNAL → SENSITIVE。
 *
 * 与资产分类分级 {@link com.mandao.grc.modules.asset.AssetClassification} 同一套三级口径，
 * 但此处用于「访问控制」语义：既是数据的分级，也是主体的数据密级（clearance）——
 * 主体密级 atLeast 数据分级方可见明文，否则脱敏。
 */
public enum DataLevel {

    /** 公开：可对外公开。 */
    PUBLIC(0),

    /** 内部：仅限组织内部；默认主体密级。 */
    INTERNAL(1),

    /** 敏感：最高级；读取敏感字段明文需具备此密级。 */
    SENSITIVE(2);

    private final int rank;

    DataLevel(int rank) {
        this.rank = rank;
    }

    public int rank() {
        return rank;
    }

    /** 本密级是否达到（≥）目标分级——达到才放行明文。 */
    public boolean atLeast(DataLevel other) {
        return this.rank >= other.rank;
    }
}
