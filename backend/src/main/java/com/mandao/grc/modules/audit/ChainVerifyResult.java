package com.mandao.grc.modules.audit;

/**
 * 哈希链校验结果。
 *
 * @param orgId       被校验的组织（链）
 * @param valid       链是否完整（无篡改、无断链、序号连续）
 * @param count       已校验的记录条数
 * @param brokenAtSeq 首个异常处的 seq；valid=true 时为 -1
 * @param reason      异常原因（篡改 / 断链 / 序号不连续）；valid=true 时为 null
 */
public record ChainVerifyResult(long orgId, boolean valid, long count,
                                long brokenAtSeq, String reason) {

    /** 构造"校验通过"结果。 */
    public static ChainVerifyResult ok(long orgId, long count) {
        return new ChainVerifyResult(orgId, true, count, -1, null);
    }

    /** 构造"校验失败"结果（在 brokenAtSeq 处发现 reason）。 */
    public static ChainVerifyResult broken(long orgId, long count, long brokenAtSeq, String reason) {
        return new ChainVerifyResult(orgId, false, count, brokenAtSeq, reason);
    }
}
