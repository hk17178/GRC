package com.mandao.grc.modules.workbench;

/**
 * 通知项（工作台·通知中心 的展示条目）。
 *
 * 来源于调度内核派发的提醒（reminder_dispatch_log）：objectType/objectId 指向业务对象（如 REG_FILING / AUDIT_PLAN），
 * eventType 为事件类型（如 REG_FILING_DUE 法定时限预警 / EXT_AUDIT_PLAN_APPROACHING 外审临近），
 * thresholdKey 为触发阈值（如 reminder_day=10），createdAtMs 为派发时间（epoch 毫秒）。
 *
 * 降噪（V41）：同一 (objectType, objectId, eventType) 的多次提醒合并为最新一条展示，
 * mergedCount 为被合并的总次数（=1 表示未发生合并）。
 * 回执（V41）：readBy/readAtMs 为已读确认人与时间（未读为 null/0）。
 */
public record NotificationView(
        long id,
        String objectType,
        long objectId,
        String eventType,
        String thresholdKey,
        long orgId,
        long createdAtMs,
        long mergedCount,
        String readBy,
        long readAtMs) {
}
