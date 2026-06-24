package com.mandao.grc;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 组织隔离红线集成测试：在真实 PostgreSQL 上验证 RLS 兜底。
 *
 * 核心断言：以【非 owner 应用角色 grc_app】连接时，即使应用层"忘记"加 org 过滤、
 * 甚至显式构造跨子公司查询/写入，数据库 RLS 仍拦截越权。
 *
 * 对应：D1-3 §5.1、D3-2 TC-M8-002 / TC-SEC-102、DM-2 R-12。
 */
@Testcontainers
class IsolationRlsTest {

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
            .withDatabaseName("grc")
            .withUsername("grc_owner")     // 容器默认用户＝超级用户/owner，执行迁移
            .withPassword("owner_pw");

    static Connection app;   // grc_app：非 owner、NOBYPASSRLS、受 RLS 约束

    @BeforeAll
    static void setup() throws Exception {
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement()) {
            // 1) 创建运行期账号（须先于迁移，因迁移内有 GRANT ... TO grc_app）
            s.execute("CREATE ROLE grc_app LOGIN PASSWORD 'grc_app_pw' NOBYPASSRLS");
            // 2) 执行与生产同一份 Flyway 迁移脚本（schema + RLS + 种子）
            String ddl = new String(
                    IsolationRlsTest.class.getResourceAsStream("/db/migration/V1__isolation_slice.sql").readAllBytes(),
                    StandardCharsets.UTF_8);
            s.execute(ddl);
        }
        app = DriverManager.getConnection(PG.getJdbcUrl(), "grc_app", "grc_app_pw");
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (app != null) app.close();
    }

    private long countWith(String visibleOrgsCsvOrNull, String whereSql) throws Exception {
        try (Statement s = app.createStatement()) {
            if (visibleOrgsCsvOrNull == null) {
                s.execute("RESET app.visible_orgs");
            } else {
                s.execute("SET app.visible_orgs = '" + visibleOrgsCsvOrNull + "'");
            }
            try (ResultSet rs = s.executeQuery("SELECT count(*) FROM assessment " + whereSql)) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    @Test
    void 支付用户仅见本子公司() throws Exception {
        assertEquals(2, countWith("12", ""));
    }

    @Test
    void 红线_跨子公司直查被RLS拦截() throws Exception {
        // scoped={12} 却显式查 org_id=13 → 必须 0 行
        assertEquals(0, countWith("12", "WHERE org_id = 13"));
    }

    @Test
    void 红线_跨子公司写入被RLS拦截() throws Exception {
        try (Statement s = app.createStatement()) {
            s.execute("SET app.visible_orgs = '12'");
            int updated = s.executeUpdate("UPDATE assessment SET risk_level='LOW' WHERE org_id=13");
            int deleted = s.executeUpdate("DELETE FROM assessment WHERE org_id=13");
            assertEquals(0, updated);
            assertEquals(0, deleted);
        }
    }

    @Test
    void 集团视角可见全集团() throws Exception {
        assertEquals(4, countWith("1,12,13", ""));
    }

    @Test
    void 红线_无隔离上下文默认拒绝() throws Exception {
        assertEquals(0, countWith(null, ""));
    }
}
