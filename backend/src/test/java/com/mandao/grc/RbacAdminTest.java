package com.mandao.grc;

import com.mandao.grc.modules.rbac.RbacAdminService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RBAC 配置管理集成测试（增强③ R5，真实 PG + V27/V28 种子）。验证：
 *  建角色 → 设权限矩阵(RW/RO/HIDDEN) → 读回(HIDDEN 不存=默认拒绝) → 角色列表含新角色。
 *
 * role/resource/role_resource 为全局字典(无 RLS)，无需隔离上下文。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "grc.scheduler.enabled=false")
@Testcontainers
class RbacAdminTest {

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
    private RbacAdminService service;

    @Test
    void 建角色_设权限矩阵_读回正确() {
        Long id = service.createRole("TESTER", "测试角色");

        service.setRolePermissions(id, Map.of(
                "risk", "RW", "dashboard", "RO", "settings", "HIDDEN"));

        Map<String, String> p = service.rolePermissions(id);
        assertEquals("RW", p.get("risk"));
        assertEquals("RO", p.get("dashboard"));
        assertNull(p.get("settings"), "HIDDEN 不应入库(默认拒绝)");

        assertTrue(service.listRoles().stream().anyMatch(r -> "TESTER".equals(r.get("code"))),
                "角色列表应含新建角色");
    }

    @Test
    void 重设矩阵_先清后插() {
        Long id = service.createRole("TESTER2", "测试角色2");
        service.setRolePermissions(id, Map.of("risk", "RW", "policy", "RO"));
        service.setRolePermissions(id, Map.of("policy", "RW")); // 重设：risk 应被清除
        Map<String, String> p = service.rolePermissions(id);
        assertEquals("RW", p.get("policy"));
        assertNull(p.get("risk"), "重设后旧项应被清除");
    }
}
