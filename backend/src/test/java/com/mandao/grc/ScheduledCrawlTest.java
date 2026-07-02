package com.mandao.grc;

import com.mandao.grc.common.isolation.IsolationContext;
import com.mandao.grc.kernel.ScheduledCrawlService;
import com.mandao.grc.modules.regulation.crawler.RegulationCrawlService;
import com.mandao.grc.modules.regulation.crawler.RegulationSource;
import com.mandao.grc.modules.regulation.crawler.SourceType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 法规爬虫定时调度内核集成测试（真实 PG + RLS）。验证：
 *  1) 跨组织选源：org12/org13 各有到期源（从未抓过），一轮调度两源都被抓，采集各归各 org；
 *  2) 到期判定：刚抓过（DAILY 间隔未到）的源，下一轮不再触发；
 *  3) 系统调度产出仍受组织隔离：org13 看不到 org12 的采集条目。
 *
 * 设计依据：需求 M4 法规跟踪（定时采集）；调度器 @Scheduled 注册由 grc.scheduler.enabled
 * 关闭，测试直接调 runOnce 保证确定性（与 ExpiryScanTest 同法）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "grc.scheduler.enabled=false")
@Testcontainers
class ScheduledCrawlTest {

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
            .withDatabaseName("grc")
            .withUsername("grc_owner")
            .withPassword("owner_pw")
            .withInitScript("testcontainers-init.sql");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PG::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "grc_app");
        registry.add("spring.datasource.password", () -> "grc_app_pw");
        registry.add("spring.flyway.url", PG::getJdbcUrl);
        registry.add("spring.flyway.user", () -> "grc_owner");
        registry.add("spring.flyway.password", () -> "owner_pw");
    }

    @Autowired
    private ScheduledCrawlService scheduler;
    @Autowired
    private RegulationCrawlService crawlService;

    private static final long ORG_PAY = 12L;
    private static final long ORG_CF = 13L;

    @BeforeEach
    void clean() throws Exception {
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement()) {
            s.executeUpdate("TRUNCATE regulation_crawled, regulation_source, operation_log RESTART IDENTITY CASCADE");
        }
    }

    @AfterEach
    void clearContext() {
        IsolationContext.clear();
    }

    @Test
    void 跨组织定时抓取_到期判定_隔离不破() {
        // 两个 org 各建一个从未抓过的 SAMPLE 源（到期）
        asOrg(ORG_PAY, () -> crawlService.addSource(ORG_PAY, "支付-法规源", SourceType.SAMPLE, null, null, "DAILY", "c"));
        asOrg(ORG_CF, () -> crawlService.addSource(ORG_CF, "消金-法规源", SourceType.SAMPLE, null, null, "DAILY", "c"));

        // 第一轮：两源都到期 → 都触发
        assertEquals(2, scheduler.runOnce(), "两个到期源都应被触发");
        assertEquals(5, asOrg(ORG_PAY, () -> crawlService.listCrawled()).size(), "org12 采集入自己名下");
        assertEquals(5, asOrg(ORG_CF, () -> crawlService.listCrawled()).size(), "org13 采集入自己名下");

        // 第二轮：刚抓过（DAILY 未到）→ 不再触发
        assertEquals(0, scheduler.runOnce(), "间隔未到不应重复触发");
        assertEquals(5, asOrg(ORG_PAY, () -> crawlService.listCrawled()).size(), "无新增");
    }

    @Test
    void 停用源不参与调度() {
        RegulationSource src = asOrg(ORG_PAY, () ->
                crawlService.addSource(ORG_PAY, "停用源", SourceType.SAMPLE, null, null, "DAILY", "c"));
        asOrg(ORG_PAY, () -> crawlService.setEnabled(src.getId(), false));

        assertEquals(0, scheduler.runOnce(), "停用源不应被触发");
        assertEquals(0, asOrg(ORG_PAY, () -> crawlService.listCrawled()).size());
    }

    private <T> T asOrg(long orgId, Supplier<T> action) {
        IsolationContext.set(List.of(orgId));
        try {
            return action.get();
        } finally {
            IsolationContext.clear();
        }
    }
}
