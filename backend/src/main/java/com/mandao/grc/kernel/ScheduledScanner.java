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

    public ScheduledScanner(ExpiryScanService expiryScanService) {
        this.expiryScanService = expiryScanService;
    }

    /** 默认每 15 分钟扫描一次（可由 grc.scheduler.fixed-delay-ms 调整）。 */
    @Scheduled(fixedDelayString = "${grc.scheduler.fixed-delay-ms:900000}")
    public void tick() {
        ScanResult result = expiryScanService.scanOnce(LocalDate.now());
        if (!result.skipped() && result.emitted() > 0) {
            log.info("到期扫描产出 {} 条时间事件", result.emitted());
        }
    }
}
