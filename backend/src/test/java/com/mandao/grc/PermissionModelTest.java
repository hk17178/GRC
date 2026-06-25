package com.mandao.grc;

import com.mandao.grc.common.isolation.IsolationContext;
import com.mandao.grc.modules.rbac.RbacPermissionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 功能级 RBAC·有效权限解析集成测试（增强③ R2，真实 PG + 切面 + RLS + V27 种子）。验证：
 *  1) 平台超管(group_admin)：对全部资源 RW（短路）；
 *  2) 受限角色(cf_user=RISK_OFFICER)：风险 RW、态势只读 RO、系统设置未授权(隐藏)；
 *  3) canWrite：风险动作可写、制度/设置不可写（默认拒绝）。
 *
 * 设计依据：用户增强诉求③ R2 权限模型。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "grc.scheduler.enabled=false")
@Testcontainers
class PermissionModelTest {

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
    private RbacPermissionService perm;

    @AfterEach
    void clearContext() {
        IsolationContext.clear();
    }

    @Test
    void 平台超管_全部资源RW() {
        Map<String, String> p = asOrg(1L, () -> perm.effectiveFor("group_admin"));
        assertEquals("RW", p.get("settings"), "超管对系统设置应 RW");
        assertEquals("RW", p.get("risk"), "超管对风险应 RW");
        assertEquals("RW", p.get("perm"), "超管对权限页应 RW");
        assertTrue(p.size() >= 30, "超管应覆盖全部资源，实际 " + p.size());
    }

    @Test
    void 受限角色_三级权限正确() {
        Map<String, String> p = asOrg(13L, () -> perm.effectiveFor("cf_user"));
        assertEquals("RW", p.get("risk"), "风险专员对风险应 RW");
        assertEquals("RW", p.get("risk.create"), "对发起评估应 RW");
        assertEquals("RO", p.get("dashboard"), "对态势应只读 RO");
        assertNull(p.get("settings"), "对系统设置应未授权(隐藏)");
        assertNull(p.get("perm"), "对权限页应未授权(隐藏)");
    }

    @Test
    void canWrite_默认拒绝() {
        assertTrue(asOrg(13L, () -> perm.canWrite("cf_user", "risk.create")), "风险动作应可写");
        assertFalse(asOrg(13L, () -> perm.canWrite("cf_user", "policy.create")), "制度新建未授权应不可写");
        assertFalse(asOrg(13L, () -> perm.canWrite("cf_user", "settings")), "系统设置未授权应不可写");
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
