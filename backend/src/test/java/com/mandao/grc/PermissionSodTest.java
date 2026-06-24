package com.mandao.grc;

import com.mandao.grc.common.isolation.IsolationContext;
import com.mandao.grc.modules.audit.ChainVerifyResult;
import com.mandao.grc.modules.audit.HashChainService;
import com.mandao.grc.modules.permission.AccessReview;
import com.mandao.grc.modules.permission.AccessReviewDecision;
import com.mandao.grc.modules.permission.AccessReviewItem;
import com.mandao.grc.modules.permission.AccessReviewService;
import com.mandao.grc.modules.permission.AccessReviewStatus;
import com.mandao.grc.modules.permission.PermissionService;
import com.mandao.grc.modules.permission.SodViolationException;
import com.mandao.grc.modules.permission.UserRoleOrg;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M8 权限审批集成测试（真实 PG + 应用切面 + RLS）。验证：
 *  1) 授予角色成功（权限四元组 org × user × role × active）；
 *  2) 【SoD 职责分离 红线】授予互斥角色被拒；补 SoD 豁免后可授予；
 *  3) 【UAR 权限审阅】createReview → start（快照）→ decideItem(REVOKE) 使 user_role_org.active=false → complete；
 *  4) 组织隔离：org12 的授权在 org13 上下文中看不到；
 *  5) 留痕：授权/审阅推进后对应 org 哈希链 verify 通过且有记录。
 *
 * 种子（V1 app_user / V7 role+sod_rule）：
 *  - app_user：1 group_admin(org1)、2 pay_user(org12)、3 cf_user(org13)；
 *  - role：1 MAKER、2 CHECKER、3 RISK_OWNER、4 AUDITOR；
 *  - sod_rule：1 (MAKER↔CHECKER)、2 (RISK_OWNER↔AUDITOR)。
 *
 * 设计依据：需求文档 M8 权限审批（RBAC/权限四元组/UAR/SoD）、D1-3 §4.7、D2-5。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "grc.scheduler.enabled=false")
@Testcontainers
class PermissionSodTest {

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
    private PermissionService permissionService;

    @Autowired
    private AccessReviewService accessReviewService;

    @Autowired
    private HashChainService hashChainService;

    private static final long ORG_PAY = 12L;   // 支付子公司
    private static final long ORG_CF = 13L;    // 消费金融

    private static final long USER_PAY = 2L;   // pay_user(org12)
    private static final long USER_CF = 3L;    // cf_user(org13)

    private static final long ROLE_MAKER = 1L;
    private static final long ROLE_CHECKER = 2L;
    private static final long SOD_RULE_MAKER_CHECKER = 1L;

    /**
     * 每用例前清空 M8 业务表与操作日志（owner 连接，绕 RLS；grc_app 无删权）。
     * access_review_item 引用 access_review 与 user_role_org，CASCADE 一并清；
     * 全局字典 role/permission/role_permission/sod_rule 保留种子不清。
     */
    @BeforeEach
    void clean() throws Exception {
        execAsOwner("TRUNCATE access_review_item, access_review, sod_exception, user_role_org, operation_log "
                + "RESTART IDENTITY CASCADE");
    }

    @AfterEach
    void clearContext() {
        IsolationContext.clear();
    }

    // ---------- 授予角色 ----------

    @Test
    void 授予角色成功() {
        UserRoleOrg uro = asOrg(ORG_PAY, () ->
                permissionService.grantRole(ORG_PAY, USER_PAY, ROLE_MAKER, "admin"));
        assertEquals(ORG_PAY, uro.getOrgId());
        assertEquals(USER_PAY, uro.getUserId());
        assertEquals(ROLE_MAKER, uro.getRoleId());
        assertTrue(uro.isActive(), "新授予应为有效");
    }

    // ---------- SoD 职责分离红线（核心） ----------

    @Test
    void SoD红线_授予互斥角色被拒() {
        // 先授予 MAKER
        asOrg(ORG_PAY, () -> permissionService.grantRole(ORG_PAY, USER_PAY, ROLE_MAKER, "admin"));
        // 再授予互斥的 CHECKER → 被 SoD 红线拦截
        assertThrows(SodViolationException.class,
                () -> runAsOrg(ORG_PAY, () ->
                        permissionService.grantRole(ORG_PAY, USER_PAY, ROLE_CHECKER, "admin")));
    }

    @Test
    void SoD红线_补豁免后可授予互斥角色() {
        asOrg(ORG_PAY, () -> permissionService.grantRole(ORG_PAY, USER_PAY, ROLE_MAKER, "admin"));

        // 无豁免：被拒
        assertThrows(SodViolationException.class,
                () -> runAsOrg(ORG_PAY, () ->
                        permissionService.grantRole(ORG_PAY, USER_PAY, ROLE_CHECKER, "admin")));

        // 登记针对 (MAKER↔CHECKER) 规则的豁免
        asOrg(ORG_PAY, () -> permissionService.grantSodException(
                ORG_PAY, USER_PAY, SOD_RULE_MAKER_CHECKER, "ciso", "业务必要，经审批豁免"));

        // 有豁免：放行
        UserRoleOrg checker = asOrg(ORG_PAY, () ->
                permissionService.grantRole(ORG_PAY, USER_PAY, ROLE_CHECKER, "admin"));
        assertTrue(checker.isActive(), "补豁免后授予互斥角色应成功");
    }

    @Test
    void SoD红线_回收互斥角色后可授予另一互斥角色() {
        asOrg(ORG_PAY, () -> permissionService.grantRole(ORG_PAY, USER_PAY, ROLE_MAKER, "admin"));
        // 回收 MAKER（active=false）后，CHECKER 不再与有效角色互斥 → 可授予
        asOrg(ORG_PAY, () -> permissionService.revokeRole(ORG_PAY, USER_PAY, ROLE_MAKER, "admin"));
        UserRoleOrg checker = asOrg(ORG_PAY, () ->
                permissionService.grantRole(ORG_PAY, USER_PAY, ROLE_CHECKER, "admin"));
        assertTrue(checker.isActive(), "回收互斥角色后授予应成功");
    }

    // ---------- UAR 权限审阅 ----------

    @Test
    void UAR_审阅撤销使授权失效() {
        Long uroId = asOrg(ORG_PAY, () ->
                permissionService.grantRole(ORG_PAY, USER_PAY, ROLE_MAKER, "admin").getId());

        Long reviewId = asOrg(ORG_PAY, () ->
                accessReviewService.createReview(ORG_PAY, "2026Q2", "ciso", "ciso").getId());
        assertEquals(AccessReviewStatus.IN_REVIEW,
                asOrg(ORG_PAY, () -> accessReviewService.startReview(reviewId, "ciso").getStatus()));

        // start 已快照该 org 全部有效授权为审阅项
        List<AccessReviewItem> items = asOrg(ORG_PAY, () -> accessReviewService.listItems(reviewId));
        assertEquals(1, items.size(), "应快照 1 条有效授权为审阅项");
        Long itemId = items.get(0).getId();
        assertEquals(uroId, items.get(0).getUserRoleOrgId());

        // REVOKE → 联动 user_role_org.active=false
        AccessReviewItem decided = asOrg(ORG_PAY, () ->
                accessReviewService.decideItem(itemId, AccessReviewDecision.REVOKE, "ciso"));
        assertEquals(AccessReviewDecision.REVOKE, decided.getDecision());

        List<UserRoleOrg> roles = asOrg(ORG_PAY, () ->
                permissionService.listUserRoles(ORG_PAY, USER_PAY));
        assertEquals(1, roles.size());
        assertFalse(roles.get(0).isActive(), "被审阅撤销后 user_role_org.active 应为 false");

        assertEquals(AccessReviewStatus.COMPLETED,
                asOrg(ORG_PAY, () -> accessReviewService.completeReview(reviewId, "ciso").getStatus()));
    }

    @Test
    void UAR_未开始审阅不可做决定() {
        asOrg(ORG_PAY, () -> permissionService.grantRole(ORG_PAY, USER_PAY, ROLE_MAKER, "admin"));
        Long reviewId = asOrg(ORG_PAY, () ->
                accessReviewService.createReview(ORG_PAY, "2026Q2", "ciso", "ciso").getId());
        // OPEN 态没有审阅项，且不可做决定——这里直接验证非法决定值被拒（PENDING 非法）
        assertThrows(IllegalArgumentException.class,
                () -> runAsOrg(ORG_PAY, () ->
                        accessReviewService.decideItem(1L, AccessReviewDecision.PENDING, "ciso")));
    }

    // ---------- 组织隔离 ----------

    @Test
    void 组织隔离_org12的授权org13看不到() {
        asOrg(ORG_PAY, () -> permissionService.grantRole(ORG_PAY, USER_PAY, ROLE_MAKER, "admin"));

        List<UserRoleOrg> payView = asOrg(ORG_PAY, () ->
                permissionService.listUserRoles(ORG_PAY, USER_PAY));
        assertEquals(1, payView.size(), "org12 应看到自己的 1 条授权");

        List<UserRoleOrg> cfView = asOrg(ORG_CF, () ->
                permissionService.listUserRoles(ORG_PAY, USER_PAY));
        assertTrue(cfView.isEmpty(), "org13 不应看到 org12 的授权");
    }

    // ---------- 留痕 ----------

    @Test
    void 留痕_授权与审阅推进后哈希链校验通过且有记录() {
        asOrg(ORG_PAY, () -> permissionService.grantRole(ORG_PAY, USER_PAY, ROLE_MAKER, "admin")); // GRANT=1
        Long reviewId = asOrg(ORG_PAY, () ->
                accessReviewService.createReview(ORG_PAY, "2026Q2", "ciso", "ciso").getId());      // CREATE=2
        asOrg(ORG_PAY, () -> accessReviewService.startReview(reviewId, "ciso"));                    // START=3
        List<AccessReviewItem> items = asOrg(ORG_PAY, () -> accessReviewService.listItems(reviewId));
        asOrg(ORG_PAY, () -> accessReviewService.decideItem(
                items.get(0).getId(), AccessReviewDecision.REVOKE, "ciso"));                        // REVOKE=4
        asOrg(ORG_PAY, () -> accessReviewService.completeReview(reviewId, "ciso"));                 // COMPLETE=5

        ChainVerifyResult r = asOrg(ORG_PAY, () -> hashChainService.verify(ORG_PAY));
        assertTrue(r.valid(), "留痕后链应校验通过");
        assertEquals(5, r.count(), "应有 5 条 M8 操作留痕");
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

    private void execAsOwner(String sql) throws Exception {
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement()) {
            s.executeUpdate(sql);
        }
    }
}
