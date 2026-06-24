package com.mandao.grc.modules.audit;

/**
 * 追加一条操作日志后的返回视图（最小信息：所属 org 链、序号、本条哈希）。
 */
public record OperationLogEntry(long orgId, long seq, String currHash) {
}
