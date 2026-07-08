package com.mandao.grc.kernel;

/**
 * 调度内核 advisory 锁 key 集中定义（七轮 7-9）。
 *
 * 教训：到期扫描/定时抓取/通知规则引擎各自私藏常量曾导致 770002 撞号——
 * 多实例部署下两个不同用途的调度互斥，静默跳轮难以排查。所有内核锁 key 一律在此登记，
 * 新增调度器时按序取号并写明用途。
 */
public final class LockKeys {

    /** 到期扫描（ExpiryScanService）。 */
    public static final long EXPIRY_SCAN = 770001L;

    /** 法规定时抓取（ScheduledCrawlService）。 */
    public static final long CRAWL = 770002L;

    /** 通知规则引擎（NotifyRuleEngine）——原与 CRAWL 撞号，七轮改号。 */
    public static final long NOTIFY_RULES = 770003L;

    /** 通知场景升级链运行器（EscalationRunner，§九 接线二）。 */
    public static final long SCENE_ESCALATION = 770004L;

    private LockKeys() {
    }
}
