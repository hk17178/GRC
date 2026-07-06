package com.mandao.grc;

import com.mandao.grc.common.isolation.IsolationContext;
import com.mandao.grc.modules.audit.management.Certificate;
import com.mandao.grc.modules.audit.management.CertificateService;
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
import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 证书有效期台账集成测试（收口批 B24，真实 PG + 切面 + RLS）。验证：
 *  1) 登记与按到期日升序列出；到期日必填；
 *  2) 吊销置 REVOKED；
 *  3) 组织隔离：org12 证书 org13 不可见。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "grc.scheduler.enabled=false")
@Testcontainers
class CertificateTest {

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
    private CertificateService service;

    private static final long ORG_PAY = 12L;
    private static final long ORG_CF = 13L;

    @BeforeEach
    void clean() throws Exception {
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement()) {
            s.executeUpdate("TRUNCATE certificate RESTART IDENTITY CASCADE");
        }
    }

    @AfterEach
    void clearContext() {
        IsolationContext.clear();
    }

    @Test
    void 登记按到期升序列出_到期日必填() {
        asOrg(ORG_PAY, () -> service.create(ORG_PAY, "PCI-DSS 认证", "PCI_DSS", "PCI-2", "QSA",
                LocalDate.of(2025, 1, 1), LocalDate.of(2027, 1, 1), "auditor"));
        asOrg(ORG_PAY, () -> service.create(ORG_PAY, "ISO27001 认证", "ISO27001", "ISO-1", "CNAS",
                LocalDate.of(2024, 6, 1), LocalDate.of(2026, 6, 1), "auditor"));

        List<Certificate> list = asOrg(ORG_PAY, () -> service.list());
        assertEquals(2, list.size());
        assertEquals("ISO27001 认证", list.get(0).getName(), "应按到期日升序：2026 在前");

        // 到期日必填
        assertThrows(IllegalArgumentException.class, () -> runAsOrg(ORG_PAY,
                () -> service.create(ORG_PAY, "无到期日", null, null, null, null, null, "auditor")));
    }

    @Test
    void 吊销置REVOKED() {
        Long id = asOrg(ORG_PAY, () -> service.create(ORG_PAY, "待吊销", "MLPS", null, null,
                null, LocalDate.of(2027, 1, 1), "auditor").getId());
        assertEquals("REVOKED", asOrg(ORG_PAY, () -> service.revoke(id, "auditor").getStatus()));
    }

    @Test
    void 组织隔离_org12证书org13不可见() {
        asOrg(ORG_PAY, () -> service.create(ORG_PAY, "仅支付可见", "ISO27001", null, null,
                null, LocalDate.of(2027, 1, 1), "auditor"));
        assertEquals(1, asOrg(ORG_PAY, () -> service.list()).size());
        assertTrue(asOrg(ORG_CF, () -> service.list()).isEmpty(), "org13 不应看到 org12 的证书");
    }

    private <T> T asOrg(long orgId, Supplier<T> action) {
        IsolationContext.set(List.of(orgId));
        try {
            return action.get();
        } finally {
            IsolationContext.clear();
        }
    }

    private void runAsOrg(long orgId, Runnable action) {
        IsolationContext.set(List.of(orgId));
        try {
            action.run();
        } finally {
            IsolationContext.clear();
        }
    }
}
