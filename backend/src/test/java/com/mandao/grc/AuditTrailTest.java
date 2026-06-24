package com.mandao.grc;

import com.mandao.grc.common.isolation.IsolationContext;
import com.mandao.grc.modules.audit.AuditTrailService;
import com.mandao.grc.modules.audit.ChainVerifyResult;
import com.mandao.grc.modules.audit.HashChainService;
import com.mandao.grc.modules.audit.OperationLogView;
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
 * 看板与留痕查询集成测试（真实 PG + 切面 + RLS + 哈希链）。验证：
 *  1) 查询留痕并按 对象/动作 过滤，按新→旧返回；
 *  2) 组织隔离：org12 留痕，org13 查询为空；
 *  3) 链完整性校验通过。
 *
 * 设计依据：需求「看板与留痕」、D1-3 §8、D2-5。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "grc.scheduler.enabled=false")
@Testcontainers
class AuditTrailTest {

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

    @Autowired
    private AuditTrailService auditTrailService;

    private static final long ORG_PAY = 12L;
    private static final long ORG_CF = 13L;

    @BeforeEach
    void clean() throws Exception {
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement()) {
            s.executeUpdate("TRUNCATE operation_log RESTART IDENTITY CASCADE");
        }
    }

    @AfterEach
    void clearContext() {
        IsolationContext.clear();
    }

    @Test
    void 查询留痕_过滤与新旧序() {
        asOrg(ORG_PAY, () -> {
            hashChainService.append(ORG_PAY, "POLICY_CREATE", "alice", "POLICY:1", "建制度");
            hashChainService.append(ORG_PAY, "POLICY_APPROVE", "bob", "POLICY:1", "审批通过");
            hashChainService.append(ORG_PAY, "FINDING_CREATE", "carol", "FINDING:9", "建发现");
            return null;
        });

        // 全量：3 条，最新在前（最后追加的 FINDING_CREATE）
        List<OperationLogView> all = asOrg(ORG_PAY, () -> auditTrailService.query(null, null, null, null));
        assertEquals(3, all.size());
        assertEquals("FINDING_CREATE", all.get(0).action(), "应按新→旧，最新在前");

        // 按对象过滤
        List<OperationLogView> byEntity = asOrg(ORG_PAY, () -> auditTrailService.query("POLICY:1", null, null, null));
        assertEquals(2, byEntity.size(), "POLICY:1 应有 2 条");

        // 按动作过滤
        List<OperationLogView> byAction = asOrg(ORG_PAY, () -> auditTrailService.query(null, "POLICY_APPROVE", null, null));
        assertEquals(1, byAction.size());
        assertEquals("bob", byAction.get(0).actor());
    }

    @Test
    void 组织隔离_org12留痕org13查不到() {
        asOrg(ORG_PAY, () -> hashChainService.append(ORG_PAY, "X", "a", "X:1", "d"));
        assertEquals(1, asOrg(ORG_PAY, () -> auditTrailService.query(null, null, null, null)).size());
        assertTrue(asOrg(ORG_CF, () -> auditTrailService.query(null, null, null, null)).isEmpty(),
                "org13 不应看到 org12 的留痕");
    }

    @Test
    void 链完整性校验通过() {
        asOrg(ORG_PAY, () -> {
            hashChainService.append(ORG_PAY, "A", "u", "E:1", "d1");
            hashChainService.append(ORG_PAY, "B", "u", "E:1", "d2");
            return null;
        });
        ChainVerifyResult r = asOrg(ORG_PAY, () -> auditTrailService.verify(ORG_PAY));
        assertTrue(r.valid(), "未被篡改的链应校验通过");
        assertEquals(2, r.count());
    }

    // ---------- 测试辅助 ----------

    private <T> T asOrg(long orgId, Supplier<T> action) {
        IsolationContext.set(List.of(orgId));
        try {
            return action.get();
        } finally {
            IsolationContext.clear();
        }
    }
}
