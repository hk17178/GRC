package com.mandao.grc.kernel;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 启用 Spring 定时调度。实际的定时触发见 {@link ScheduledScanner}。
 * 生产环境建议升级为 Quartz + ShedLock（分布式锁更健壮，见 D1-9 H-01）；
 * 本切片用 @Scheduled + 数据库 advisory 锁达成同等"单实例触发"效果。
 */
@Configuration
@EnableScheduling
public class SchedulerConfig {
}
