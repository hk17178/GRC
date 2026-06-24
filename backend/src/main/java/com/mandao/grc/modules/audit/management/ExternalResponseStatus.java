package com.mandao.grc.modules.audit.management;

/**
 * 外部审计对外回函三段漏斗状态（M3 红线）。
 *
 * 漏斗仅 {@link AuditType#EXTERNAL} 的审计发现可用，单向推进、不可跳级、不可逆向：
 *   SUBMITTED(已提交外部机构) → ACCEPTED(外方受理) → CLOSED(外方确认关闭)
 *
 * 关键约束（由 {@link AuditFindingService} 强制，非法流转抛 {@link AuditFunnelException}）：
 *   1) 起点：未进入漏斗(null) 仅能进入 SUBMITTED；
 *   2) 单步前进：只能推进到 {@link #next()}（相邻下一段），跨段(如 SUBMITTED→CLOSED)被拒；
 *   3) 不可逆：禁止退回任何更早段（含原地重复）；
 *   4) 闭环：唯 {@link #CLOSED} 算外审闭环（{@link #isClosed()}）。
 *
 * ordinal 顺序即漏斗推进顺序，{@link #order()} 据此判定前进/逆向/跳级。
 *
 * 设计依据：需求文档 M3 审计管理（外审三段漏斗 external_status：SUBMITTED/ACCEPTED/CLOSED）。
 */
public enum ExternalResponseStatus {

    /** 第一段：已提交外部机构。 */
    SUBMITTED,

    /** 第二段：外方受理。 */
    ACCEPTED,

    /** 第三段（闭环）：外方确认关闭——唯一算外审闭环的终态。 */
    CLOSED;

    /** 漏斗序号（0=SUBMITTED, 1=ACCEPTED, 2=CLOSED），用于前进/逆向/跳级判定。 */
    public int order() {
        return ordinal();
    }

    /** 是否为外审闭环终态（唯 CLOSED）。 */
    public boolean isClosed() {
        return this == CLOSED;
    }

    /** 相邻下一段；终态无下一段返回 null。 */
    public ExternalResponseStatus next() {
        ExternalResponseStatus[] all = values();
        return ordinal() + 1 < all.length ? all[ordinal() + 1] : null;
    }
}
