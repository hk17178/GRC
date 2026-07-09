package com.mandao.grc;

import com.mandao.grc.common.isolation.IsolationContext;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 法规跟踪爬虫集成测试（真实 PG + 切面 + RLS）。验证：
 *  1) SAMPLE 源采集：首次抓取入库 5 条；
 *  2) 增量去重：再次抓取命中 5 但新增 0；
 *  3) 源状态回写：last_hit_count / status=OK；
 *  4) 组织隔离：org12 的源与采集 org13 不可见。
 *
 * 设计依据：需求 M4 法规跟踪（权威信息源采集）、D2-5。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "grc.scheduler.enabled=false")
@Testcontainers
class RegulationCrawlTest {

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
    private RegulationCrawlService service;

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
    void 示例源采集_增量去重_状态回写() {
        RegulationSource src = asOrg(ORG_PAY, () ->
                service.addSource(ORG_PAY, "全国法规库（示例）", SourceType.SAMPLE, null, null, "DAILY", "c"));

        // 首次抓取：命中 5、新增 5
        RegulationCrawlService.CrawlResult r1 = asOrg(ORG_PAY, () -> service.crawl(src.getId(), "c"));
        assertEquals(5, r1.hit());
        assertEquals(5, r1.added());
        assertEquals(5, asOrg(ORG_PAY, () -> service.listCrawled()).size());

        // 再次抓取：命中 5、新增 0（去重）
        RegulationCrawlService.CrawlResult r2 = asOrg(ORG_PAY, () -> service.crawl(src.getId(), "c"));
        assertEquals(5, r2.hit());
        assertEquals(0, r2.added());
        assertEquals(5, asOrg(ORG_PAY, () -> service.listCrawled()).size(), "去重后仍 5 条");

        // 源状态回写
        RegulationSource reloaded = asOrg(ORG_PAY, () -> service.listSources().get(0));
        assertEquals(5, reloaded.getLastHitCount());
        assertEquals("OK", reloaded.getStatus());
    }

    @Test
    void 关键字过滤_仅采纳命中条目() {
        // 源配 keyword=反洗钱 → 5 条示例中仅「支付机构反洗钱和反恐怖融资管理办法」命中入库
        RegulationSource src = asOrg(ORG_PAY, () ->
                service.addSource(ORG_PAY, "反洗钱专项源", SourceType.SAMPLE, null, "{\"keyword\":\"反洗钱\"}", "DAILY", "c"));
        RegulationCrawlService.CrawlResult r = asOrg(ORG_PAY, () -> service.crawl(src.getId(), "c"));
        assertEquals(1, r.hit(), "关键字过滤后命中 1 条");
        assertEquals(1, r.added());
        var rows = asOrg(ORG_PAY, () -> service.listCrawled());
        assertEquals(1, rows.size());
        assertTrue(rows.get(0).getTitle().contains("反洗钱"), "入库的应是命中关键字的条目");
    }

    @Test
    void 组织隔离_org12源与采集org13不可见() {
        RegulationSource src = asOrg(ORG_PAY, () ->
                service.addSource(ORG_PAY, "源X", SourceType.SAMPLE, null, null, "DAILY", "c"));
        asOrg(ORG_PAY, () -> service.crawl(src.getId(), "c"));

        assertEquals(1, asOrg(ORG_PAY, () -> service.listSources()).size());
        assertTrue(asOrg(ORG_CF, () -> service.listSources()).isEmpty(), "org13 不应看到 org12 的源");
        assertTrue(asOrg(ORG_CF, () -> service.listCrawled()).isEmpty(), "org13 不应看到 org12 的采集");
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
