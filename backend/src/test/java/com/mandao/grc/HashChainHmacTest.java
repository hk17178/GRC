package com.mandao.grc;

import com.mandao.grc.common.isolation.IsolationContext;
import com.mandao.grc.modules.audit.ChainVerifyResult;
import com.mandao.grc.modules.audit.HashChainService;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 哈希链完整性加固测试（安全评审 H-1 / M-4）：
 *  - keyed-HMAC 链完好时校验通过；直连库篡改内容被发现；
 *  - 直连库删除链尾（链本身仍自洽）被链尖锚定判为截断。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = "grc.scheduler.enabled=false")
@Testcontainers
class HashChainHmacTest {

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
            .withDatabaseName("grc").withUsername("grc_owner").withPassword("owner_pw")
            .withInitScript("testcontainers-init.sql");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", PG::getJdbcUrl);
        r.add("spring.datasource.username", () -> "grc_app");
        r.add("spring.datasource.password", () -> "grc_app_pw");
        r.add("spring.flyway.url", PG::getJdbcUrl);
        r.add("spring.flyway.user", () -> "grc_owner");
        r.add("spring.flyway.password", () -> "owner_pw");
    }

    @Autowired
    private HashChainService hashChainService;

    private static final long ORG = 12L;

    @BeforeEach
    void clean() throws Exception {
        execOwner("TRUNCATE operation_log, operation_log_anchor CASCADE");
    }

    @AfterEach
    void clearCtx() {
        IsolationContext.clear();
    }

    @Test
    void hmac链_完好校验通过_篡改被发现() throws Exception {
        asOrg(ORG, () -> hashChainService.append(ORG, "A", "u", "E:1", "d1"));
        asOrg(ORG, () -> hashChainService.append(ORG, "B", "u", "E:2", "d2"));
        asOrg(ORG, () -> hashChainService.append(ORG, "C", "u", "E:3", "d3"));
        assertTrue(asOrg(ORG, () -> hashChainService.verify(ORG)).valid(), "完好 HMAC 链应校验通过");

        // 直连库篡改第 2 条内容 → keyed-HMAC 无密钥不可同步伪造 curr_hash，校验应失败
        execOwner("UPDATE operation_log SET detail = 'tampered' WHERE org_id = 12 AND seq = 2");
        assertFalse(asOrg(ORG, () -> hashChainService.verify(ORG)).valid(), "篡改应被发现");
    }

    @Test
    void 链尾截断被锚定发现() throws Exception {
        asOrg(ORG, () -> hashChainService.append(ORG, "A", "u", "E:1", "d1"));
        asOrg(ORG, () -> hashChainService.append(ORG, "B", "u", "E:2", "d2"));
        asOrg(ORG, () -> hashChainService.append(ORG, "C", "u", "E:3", "d3"));
        // 直连库删除链尾（seq=3）：剩余链仍自洽，但链尖锚定 max_seq=3 → 判截断
        execOwner("DELETE FROM operation_log WHERE org_id = 12 AND seq = 3");
        ChainVerifyResult r = asOrg(ORG, () -> hashChainService.verify(ORG));
        assertFalse(r.valid(), "链尾截断应被链尖锚定发现");
        assertTrue(r.reason() != null && r.reason().contains("截断"), "原因应指明截断：" + r.reason());
    }

    private void execOwner(String sql) throws Exception {
        try (Connection c = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = c.createStatement()) {
            s.executeUpdate(sql);
        }
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
