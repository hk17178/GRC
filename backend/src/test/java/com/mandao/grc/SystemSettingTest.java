package com.mandao.grc;

import com.mandao.grc.common.isolation.IsolationContext;
import com.mandao.grc.modules.audit.ChainVerifyResult;
import com.mandao.grc.modules.audit.HashChainService;
import com.mandao.grc.modules.settings.SettingValueType;
import com.mandao.grc.modules.settings.SystemSettingService;
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
import java.util.concurrent.Callable;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 系统设置集成测试（真实 PG + 切面 + RLS）。验证：
 *  1) 定义/更新配置；INT 类型非法值被拒；
 *  2) 系统锁定项(editable=false)不可改；
 *  3) 组织隔离：org12 配置，org13 看不到（各租户独立配置）；
 *  4) 留痕：定义/更新哈希链校验通过且计数正确。
 *
 * 设计依据：需求 系统设置、D1-8 可配置性、D2-5。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "grc.scheduler.enabled=false")
@Testcontainers
class SystemSettingTest {

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
    private SystemSettingService settingService;

    @Autowired
    private HashChainService hashChainService;

    private static final long ORG_PAY = 12L;
    private static final long ORG_CF = 13L;

    @BeforeEach
    void clean() throws Exception {
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement()) {
            s.executeUpdate("TRUNCATE system_setting, operation_log RESTART IDENTITY CASCADE");
        }
    }

    @AfterEach
    void clearContext() {
        IsolationContext.clear();
    }

    @Test
    void 定义更新_类型校验与锁定项() {
        // 可改的 INT 配置
        Long id = asOrg(ORG_PAY, () -> settingService.define(ORG_PAY, "reminder.days", "10",
                SettingValueType.INT, "reminder", "法定时限提醒天数", true, "admin").getId());
        // 合法更新
        assertEquals("7", asOrg(ORG_PAY, () -> settingService.update(id, "7", "admin").getSettingValue()));
        // INT 非法值被拒
        assertThrows(IllegalArgumentException.class,
                () -> runAsOrg(ORG_PAY, () -> settingService.update(id, "abc", "admin")));

        // 系统锁定项(editable=false)不可改
        Long locked = asOrg(ORG_PAY, () -> settingService.define(ORG_PAY, "isolation.mode", "RLS",
                SettingValueType.STRING, "security", "隔离模式（系统锁定）", false, "admin").getId());
        assertThrows(IllegalStateException.class,
                () -> runAsOrg(ORG_PAY, () -> settingService.update(locked, "OFF", "admin")));
    }

    @Test
    void 定义BOOL_非法值被拒() {
        assertThrows(IllegalArgumentException.class,
                () -> runAsOrg(ORG_PAY, () -> settingService.define(ORG_PAY, "feature.x", "yes",
                        SettingValueType.BOOL, "feature", "开关", true, "admin")));
    }

    @Test
    void 组织隔离_org12配置org13看不到() {
        asOrg(ORG_PAY, () -> settingService.define(ORG_PAY, "k", "v", SettingValueType.STRING, "c", "d", true, "admin"));
        assertEquals(1, asOrg(ORG_PAY, () -> settingService.list()).size(), "org12 应看到自己的 1 条配置");
        assertTrue(asOrg(ORG_CF, () -> settingService.list()).isEmpty(), "org13 不应看到 org12 的配置");
    }

    @Test
    void 留痕_定义更新共2条() {
        Long id = asOrg(ORG_PAY, () -> settingService.define(ORG_PAY, "k", "1", SettingValueType.INT, "c", "d", true, "admin").getId()); // DEFINE
        asOrg(ORG_PAY, () -> settingService.update(id, "2", "admin"));                                                                    // UPDATE

        ChainVerifyResult r = asOrg(ORG_PAY, () -> hashChainService.verify(ORG_PAY));
        assertTrue(r.valid(), "留痕后链应校验通过");
        assertEquals(2, r.count(), "应有 2 条配置留痕");
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

    private void runAsOrg(long orgId, Callable<?> action) throws Exception {
        IsolationContext.set(List.of(orgId));
        try {
            action.call();
        } finally {
            IsolationContext.clear();
        }
    }
}
