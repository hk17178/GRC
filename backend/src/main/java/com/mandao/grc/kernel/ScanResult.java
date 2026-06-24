package com.mandao.grc.kernel;

/**
 * 一次到期扫描的结果。
 *
 * @param emitted 本次新产出的事件条数（幂等去重后）
 * @param skipped 是否因未抢到单实例锁而跳过（多实例下另一个实例正在扫描）
 */
public record ScanResult(int emitted, boolean skipped) {
}
