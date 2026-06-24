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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 防篡改哈希链集成测试（真实 PG + 应用切面）。验证：
 *  1) 追加后链完整、校验通过；
 *  2) 即便有人【直连数据库绕过应用】篡改某行，重算校验也能发现并定位；
 *  3) 各 org 链相互独立，一条链被篡改不影响另一条。
 *
 * 设计依据：D1-3 §8（ADR-C）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class HashChainTest {

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
    private HashChainService hashChainService;

    /**
     * 每个用例前清空操作日志：静态容器在本类多个用例间共享，必须隔离测试数据，
     * 否则前一用例的篡改/追加会污染后一用例。以 owner 连接 TRUNCATE（grc_app 无删权）。
     */
    @BeforeEach
    void cleanLog() throws Exception {
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement()) {
            s.executeUpdate("TRUNCATE operation_log RESTART IDENTITY");
        }
    }

    @AfterEach
    void clearContext() {
        IsolationContext.clear();
    }

    /** 在指定 org 可见上下文中追加若干条日志。 */
    private void appendAs(long orgId, String... actions) {
        IsolationContext.set(List.of(orgId));
        try {
            for (String a : actions) {
                hashChainService.append(orgId, a, "tester", "demo:1", "{\"k\":\"" + a + "\"}");
            }
        } finally {
            IsolationContext.clear();
        }
    }

    private ChainVerifyResult verifyAs(long orgId) {
        IsolationContext.set(List.of(orgId));
        try {
            return hashChainService.verify(orgId);
        } finally {
            IsolationContext.clear();
        }
    }

    @Test
    void 链完整_追加后校验通过() {
        appendAs(12L, "LOGIN", "UPDATE_POLICY", "EXPORT");
        ChainVerifyResult r = verifyAs(12L);
        assertTrue(r.valid(), "新链应校验通过");
        assertEquals(3, r.count());
    }

    @Test
    void 红线_直连数据库篡改某行被检测() throws Exception {
        appendAs(12L, "LOGIN", "UPDATE_POLICY", "EXPORT");
        assertTrue(verifyAs(12L).valid());

        // 模拟攻击者以 owner 直连 DB（绕过应用与仅追加授权）篡改第 2 条内容
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement()) {
            s.executeUpdate("UPDATE operation_log SET detail = '{\"tampered\":true}' WHERE org_id = 12 AND seq = 2");
        }

        ChainVerifyResult r = verifyAs(12L);
        assertFalse(r.valid(), "篡改后校验必须失败");
        assertEquals(2, r.brokenAtSeq(), "应定位到被篡改的 seq=2");
    }

    @Test
    void 各org链相互独立_一链被篡改不影响另一链() throws Exception {
        appendAs(12L, "LOGIN", "EXPORT");
        appendAs(13L, "LOGIN", "EXPORT");
        assertTrue(verifyAs(12L).valid());
        assertTrue(verifyAs(13L).valid());

        // 篡改 org 12 的链
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement()) {
            s.executeUpdate("UPDATE operation_log SET actor = 'evil' WHERE org_id = 12 AND seq = 1");
        }

        assertFalse(verifyAs(12L).valid(), "org12 链应被检出篡改");
        assertTrue(verifyAs(13L).valid(), "org13 链不受影响，仍完整");
    }
}
