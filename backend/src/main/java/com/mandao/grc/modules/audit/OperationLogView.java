package com.mandao.grc.modules.audit;

/**
 * 操作留痕查询视图（看板与留痕：防篡改哈希链的只读展示行）。
 *
 * curr_hash 一并返回，便于前端展示"已入链"凭据；完整性以 {@link ChainVerifyResult} 单独校验。
 */
public record OperationLogView(
        long orgId,
        long seq,
        String action,
        String actor,
        String entity,
        String detail,
        long createdAtMs,
        String currHash) {
}
