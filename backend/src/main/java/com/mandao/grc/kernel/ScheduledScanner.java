package com.mandao.grc.kernel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 定时触发器：按固定间隔调用到期扫描内核。
 *
 * 通过 grc.scheduler.enabled 开关（默认开）控制是否注册——测试中关闭，避免定时任务干扰；
 * 测试直接调用 {@link ExpiryScanService#scanOnce} 以保证确定性。
 */
@Component
@ConditionalOnProperty(name = "grc.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class ScheduledScanner {

    private static final Logger log = LoggerFactory.getLogger(ScheduledScanner.class);

    private final ExpiryScanService expiryScanService;
    private final ScheduledCrawlService scheduledCrawlService;
    private final NotifyRuleEngine notifyRuleEngine;
    private final AlertPushService alertPushService;

    public ScheduledScanner(ExpiryScanService expiryScanService, ScheduledCrawlService scheduledCrawlService,
                            NotifyRuleEngine notifyRuleEngine, AlertPushService alertPushService) {
        this.expiryScanService = expiryScanService;
        this.scheduledCrawlService = scheduledCrawlService;
        this.notifyRuleEngine = notifyRuleEngine;
        this.alertPushService = alertPushService;
    }

    /** 默认每 15 分钟扫描一次（可由 grc.scheduler.fixed-delay-ms 调整）。 */
    @Scheduled(fixedDelayString = "${grc.scheduler.fixed-delay-ms:900000}")
    public void tick() {
        ScanResult result = expiryScanService.scanOnce(LocalDate.now());
        if (!result.skipped() && result.emitted() > 0) {
            log.info("到期扫描产出 {} 条时间事件", result.emitted());
        }
        // 通知规则引擎与到期扫描同节拍（六轮 #7）；新告警在事务外做企微外推（八轮 8-1）
        java.util.List<AlertPushService.Alert> fresh = new java.util.ArrayList<>();
        int produced = notifyRuleEngine.runOnce(LocalDate.now(), fresh);
        if (produced > 0) {
            log.info("通知规则引擎产出 {} 条告警", produced);
        }
        alertPushService.pushAll(fresh);
    }

    /** 法规爬虫定时抓取：默认每 30 分钟选一次到期源（可由 grc.crawler.fixed-delay-ms 调整）。 */
    @Scheduled(fixedDelayString = "${grc.crawler.fixed-delay-ms:1800000}")
    public void crawlTick() {
        int triggered = scheduledCrawlService.runOnce();
        if (triggered > 0) {
            log.info("定时抓取触发 {} 个法规追踪源", triggered);
        }
    }
}
