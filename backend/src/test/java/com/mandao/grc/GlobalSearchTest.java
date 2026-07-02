package com.mandao.grc;

import com.mandao.grc.common.isolation.IsolationContext;
import com.mandao.grc.modules.policy.PolicyService;
import com.mandao.grc.modules.regulation.RegulationService;
import com.mandao.grc.modules.search.SearchService;
import com.mandao.grc.modules.vendor.VendorService;
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
 * 全局搜索集成测试（真实 PG + 切面 + RLS）。验证：
 *  1) 跨对象命中：法规/制度/供应商 同词命中且 module 标注正确；
 *  2) 组织隔离：org13 搜不到 org12 的数据（RLS 生效于搜索）；
 *  3) 空词返回空。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "grc.scheduler.enabled=false")
@Testcontainers
class GlobalSearchTest {

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

    @Autowired private SearchService searchService;
    @Autowired private RegulationService regulationService;
    @Autowired private PolicyService policyService;
    @Autowired private VendorService vendorService;

    private static final long ORG_PAY = 12L;
    private static final long ORG_CF = 13L;

    @BeforeEach
    void clean() throws Exception {
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement()) {
            s.executeUpdate("TRUNCATE regulation_change, regulation, vendor_assessment, vendor, operation_log RESTART IDENTITY CASCADE");
            s.executeUpdate("DELETE FROM policy WHERE id >= 1000 OR title LIKE '%清算%'");
        }
    }

    @AfterEach
    void clearContext() {
        IsolationContext.clear();
    }

    @Test
    void 跨对象命中_模块标注正确() {
        asOrg(ORG_PAY, () -> regulationService.create(ORG_PAY, "REG-QS", "非银行支付机构清算条例", "PBOC", "支付清算", null, null, "c"));
        asOrg(ORG_PAY, () -> policyService.create(ORG_PAY, "POL-QS", "清算业务管理制度", "内容", "c"));
        asOrg(ORG_PAY, () -> vendorService.register(ORG_PAY, "VD-QS", "清算服务供应商A", "清算", null, "HIGH", "c"));

        List<SearchService.SearchHit> hits = asOrg(ORG_PAY, () -> searchService.search("清算"));
        assertTrue(hits.size() >= 3, "法规/制度/供应商应各命中：" + hits);
        assertTrue(hits.stream().anyMatch(h -> h.module().equals("regulation")), "应命中法规");
        assertTrue(hits.stream().anyMatch(h -> h.module().equals("policy")), "应命中制度");
        assertTrue(hits.stream().anyMatch(h -> h.module().equals("vendor")), "应命中供应商");
    }

    @Test
    void 组织隔离_org13搜不到org12数据() {
        asOrg(ORG_PAY, () -> regulationService.create(ORG_PAY, "REG-ISO", "隔离验证专用法规", "X", "test", null, null, "c"));
        assertEquals(1, asOrg(ORG_PAY, () -> searchService.search("隔离验证专用")).size());
        assertTrue(asOrg(ORG_CF, () -> searchService.search("隔离验证专用")).isEmpty(), "org13 不应搜到 org12 的数据");
    }

    @Test
    void 空词返回空() {
        assertTrue(asOrg(ORG_PAY, () -> searchService.search("  ")).isEmpty());
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
