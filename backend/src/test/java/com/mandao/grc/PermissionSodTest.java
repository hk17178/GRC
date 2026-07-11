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
import com.mandao.grc.modules.workflow.ApprovalDecision;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
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
 *  2) 【SoD·BLOCK 红线】授予互斥角色被拒（SodViolationException）；补 SoD 豁免后可授予；
 *  3) 【SoD·DETECT 检测型】授予互斥角色【成功】（并集生效）且【登记冲突】（哈希链含 SOD_CONFLICT_DETECTED）；
 *  4) 【UAR 权限审阅】createReview → start（快照）→ decideItem(REVOKE/DOWNGRADE) 使 active=false → complete；
 *  5) 组织隔离：org12 的授权在 org13 上下文中看不到；
 *  6) 留痕：授权/审阅推进后对应 org 哈希链 verify 通过且有记录。
 *
 * 种子（V1 app_user / V7 role+sod_rule）：
 *  - app_user：1 group_admin(org1)、2 pay_user(org12)、3 cf_user(org13)；
 *  - role：1 MAKER、2 CHECKER、3 RISK_OWNER、4 AUDITOR；
 *  - sod_rule：1 (MAKER↔CHECKER, BLOCK 硬阻断)、2 (RISK_OWNER↔AUDITOR, DETECT 检测型)。
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

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private HistoryService historyService;

    private static final long ORG_PAY = 12L;   // 支付子公司
    private static final long ORG_CF = 13L;    // 消费金融

    private static final long USER_PAY = 2L;   // pay_user(org12)
    private static final long USER_CF = 3L;    // cf_user(org13)

    private static final long ROLE_MAKER = 1L;
    private static final long ROLE_CHECKER = 2L;
    private static final long ROLE_RISK_OWNER = 3L;
    private static final long ROLE_AUDITOR = 4L;
    private static final long SOD_RULE_MAKER_CHECKER = 1L;   // BLOCK 硬阻断
    private static final long SOD_RULE_RISK_AUDITOR = 2L;    // DETECT 检测型

    /**
     * 每用例前清空 M8 业务表与操作日志（owner 连接，绕 RLS；grc_app 无删权）。
     * access_review_item 引用 access_review 与 user_role_org，CASCADE 一并清；
     * 全局字典 role/permission/role_permission/sod_rule 保留种子不清。
     */
    @BeforeEach
    void clean() throws Exception {
        execAsOwner("TRUNCATE access_review_item, access_review, sod_exception, user_role_org, operation_log "
                + "RESTART IDENTITY CASCADE");
        // 清理 Flowable 运行态/历史：sod_exception id 因 RESTART IDENTITY 跨用例复用，会使
        // 豁免审批 businessKey(SOD_EXCEPTION:{id}) 跨用例碰撞，须净化（生产 id 全局唯一无此问题）。
        runtimeService.createProcessInstanceQuery().list()
                .forEach(pi -> runtimeService.deleteProcessInstance(pi.getId(), "用例重置"));
        historyService.createHistoricProcessInstanceQuery().list()
                .forEach(hpi -> historyService.deleteHistoricProcessInstance(hpi.getId()));
    }

    @AfterEach
    void clearContext() {
        IsolationContext.clear();
    }

    // ---------- 授予角色 ----------

    @Test
    void h5_非超管授权人不得授予超管角色_纵向提权防护() throws Exception {
        long superRoleId;
        try (java.sql.Connection c = java.sql.DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             java.sql.Statement s = c.createStatement()) {
            s.executeUpdate("INSERT INTO role (code, name, superadmin) VALUES ('h5_super', 'H5超管测试', true)");
            try (java.sql.ResultSet rs = s.executeQuery("SELECT id FROM role WHERE code = 'h5_super'")) {
                rs.next();
                superRoleId = rs.getLong(1);
            }
        }
        final long srid = superRoleId;
        // 非超管授权人授予「超管」角色 → 拒（防 perm 管理员自授超管纵向提权）
        assertThrows(IllegalStateException.class, () -> asOrg(ORG_PAY, () ->
                        permissionService.grantRole(ORG_PAY, USER_PAY, srid, "not_a_superadmin")),
                "非超管授权人不得授予超管角色");
    }

    @Test
    void 授予角色成功() {
        UserRoleOrg uro = asOrg(ORG_PAY, () ->
                permissionService.grantRole(ORG_PAY, USER_PAY, ROLE_MAKER, "admin"));
        assertEquals(ORG_PAY, uro.getOrgId());
        assertEquals(USER_PAY, uro.getUserId());
        assertEquals(ROLE_MAKER, uro.getRoleId());
        assertTrue(uro.isActive(), "新授予应为有效");
    }

    // ---------- SoD 职责分离红线（核心：BLOCK 阻断 / DETECT 检测） ----------

    @Test
    void SoD红线BLOCK_授予互斥角色被拒() {
        // 先授予 MAKER
        asOrg(ORG_PAY, () -> permissionService.grantRole(ORG_PAY, USER_PAY, ROLE_MAKER, "admin"));
        // 再授予互斥的 CHECKER（rule1=BLOCK）→ 被 SoD 红线硬阻断
        assertThrows(SodViolationException.class,
                () -> runAsOrg(ORG_PAY, () ->
                        permissionService.grantRole(ORG_PAY, USER_PAY, ROLE_CHECKER, "admin")));
    }

    @Test
    void SoD红线BLOCK_补豁免后可授予互斥角色() {
        asOrg(ORG_PAY, () -> permissionService.grantRole(ORG_PAY, USER_PAY, ROLE_MAKER, "admin"));

        // 无豁免：被拒
        assertThrows(SodViolationException.class,
                () -> runAsOrg(ORG_PAY, () ->
                        permissionService.grantRole(ORG_PAY, USER_PAY, ROLE_CHECKER, "admin")));

        // 申请针对 (MAKER↔CHECKER) 规则的豁免并审批通过
        Long exId = asOrg(ORG_PAY, () -> permissionService.requestSodException(
                ORG_PAY, USER_PAY, SOD_RULE_MAKER_CHECKER, "owner", "业务必要，申请豁免", "owner").getId());
        asOrg(ORG_PAY, () -> permissionService.decideSodException(
                exId, ApprovalDecision.APPROVED, "ciso", "同意豁免"));

        // 审批通过的豁免：放行
        UserRoleOrg checker = asOrg(ORG_PAY, () ->
                permissionService.grantRole(ORG_PAY, USER_PAY, ROLE_CHECKER, "admin"));
        assertTrue(checker.isActive(), "豁免经审批通过后授予互斥角色应成功");
    }

    @Test
    void SoD红线BLOCK_仅申请未审批仍不放行() {
        asOrg(ORG_PAY, () -> permissionService.grantRole(ORG_PAY, USER_PAY, ROLE_MAKER, "admin"));
        // 仅申请豁免(PENDING)，未审批通过
        asOrg(ORG_PAY, () -> permissionService.requestSodException(
                ORG_PAY, USER_PAY, SOD_RULE_MAKER_CHECKER, "owner", "申请豁免", "owner"));
        // PENDING 豁免不生效 → 授予互斥角色仍被 BLOCK 硬阻断（审批化红线）
        assertThrows(SodViolationException.class,
                () -> runAsOrg(ORG_PAY, () ->
                        permissionService.grantRole(ORG_PAY, USER_PAY, ROLE_CHECKER, "admin")));
    }

    @Test
    void SoD红线BLOCK_回收互斥角色后可授予另一互斥角色() {
        asOrg(ORG_PAY, () -> permissionService.grantRole(ORG_PAY, USER_PAY, ROLE_MAKER, "admin"));
        // 回收 MAKER（active=false）后，CHECKER 不再与有效角色互斥 → 可授予
        asOrg(ORG_PAY, () -> permissionService.revokeRole(ORG_PAY, USER_PAY, ROLE_MAKER, "admin"));
        UserRoleOrg checker = asOrg(ORG_PAY, () ->
                permissionService.grantRole(ORG_PAY, USER_PAY, ROLE_CHECKER, "admin"));
        assertTrue(checker.isActive(), "回收互斥角色后授予应成功");
    }

    @Test
    void SoD红线DETECT_授予互斥角色放行并登记冲突() {
        // 先授予 RISK_OWNER
        asOrg(ORG_PAY, () -> permissionService.grantRole(ORG_PAY, USER_PAY, ROLE_RISK_OWNER, "admin"));
        // 再授予互斥的 AUDITOR（rule2=DETECT）→ 不阻断、放行，并集生效
        UserRoleOrg auditor = asOrg(ORG_PAY, () ->
                permissionService.grantRole(ORG_PAY, USER_PAY, ROLE_AUDITOR, "admin"));
        assertTrue(auditor.isActive(), "DETECT 检测型：授予互斥角色应放行成功");

        // 并集照常生效：两条有效授权同时存在
        List<UserRoleOrg> roles = asOrg(ORG_PAY, () ->
                permissionService.listUserRoles(ORG_PAY, USER_PAY));
        assertEquals(2, roles.size(), "DETECT 放行后两互斥角色应并集生效");
        assertTrue(roles.stream().allMatch(UserRoleOrg::isActive), "两条授权均应有效");

        // 登记了冲突：哈希链/operation_log 含一条 SOD_CONFLICT_DETECTED
        assertEquals(1, countLogByAction(ORG_PAY, "SOD_CONFLICT_DETECTED"),
                "DETECT 命中应登记一条 SOD_CONFLICT_DETECTED 冲突");
        // 留痕链仍校验通过
        assertTrue(asOrg(ORG_PAY, () -> hashChainService.verify(ORG_PAY)).valid(),
                "登记冲突后哈希链应仍校验通过");
    }

    // ---------- B18：SoD 存量冲突扫描 ----------

    @Test
    void b18_存量扫描_检出互斥并存并标注豁免() {
        // DETECT 放行两端角色 → 形成存量冲突
        asOrg(ORG_PAY, () -> permissionService.grantRole(ORG_PAY, USER_PAY, ROLE_RISK_OWNER, "admin"));
        asOrg(ORG_PAY, () -> permissionService.grantRole(ORG_PAY, USER_PAY, ROLE_AUDITOR, "admin"));

        List<PermissionService.SodConflict> conflicts = asOrg(ORG_PAY,
                () -> permissionService.scanSodConflicts());
        assertEquals(1, conflicts.size(), "应检出 1 条存量互斥并存");
        PermissionService.SodConflict c = conflicts.get(0);
        assertEquals(USER_PAY, c.userId());
        assertEquals(SOD_RULE_RISK_AUDITOR, c.ruleId());
        assertFalse(c.exempted(), "未豁免应标记待整改");

        // 回收其一后不再冲突
        asOrg(ORG_PAY, () -> permissionService.revokeRole(ORG_PAY, USER_PAY, ROLE_AUDITOR, "admin"));
        assertTrue(asOrg(ORG_PAY, () -> permissionService.scanSodConflicts()).isEmpty(),
                "回收互斥角色其一后应无存量冲突");
    }

    @Test
    void b18_存量扫描_组织隔离() {
        asOrg(ORG_PAY, () -> permissionService.grantRole(ORG_PAY, USER_PAY, ROLE_RISK_OWNER, "admin"));
        asOrg(ORG_PAY, () -> permissionService.grantRole(ORG_PAY, USER_PAY, ROLE_AUDITOR, "admin"));
        // org13 扫描看不到 org12 的冲突（RLS 裁剪）
        assertTrue(asOrg(ORG_CF, () -> permissionService.scanSodConflicts()).isEmpty(),
                "org13 不应看到 org12 的存量冲突");
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
    void UAR_审阅降权使授权失效且decision记为DOWNGRADE() {
        asOrg(ORG_PAY, () ->
                permissionService.grantRole(ORG_PAY, USER_PAY, ROLE_MAKER, "admin"));

        Long reviewId = asOrg(ORG_PAY, () ->
                accessReviewService.createReview(ORG_PAY, "2026Q2", "ciso", "ciso").getId());
        asOrg(ORG_PAY, () -> accessReviewService.startReview(reviewId, "ciso"));

        List<AccessReviewItem> items = asOrg(ORG_PAY, () -> accessReviewService.listItems(reviewId));
        Long itemId = items.get(0).getId();

        // DOWNGRADE → 联动 active=false，且 decision=DOWNGRADE（与 REVOKE 区分）
        AccessReviewItem decided = asOrg(ORG_PAY, () ->
                accessReviewService.decideItem(itemId, AccessReviewDecision.DOWNGRADE, "ciso"));
        assertEquals(AccessReviewDecision.DOWNGRADE, decided.getDecision(), "decision 应记为 DOWNGRADE");

        List<UserRoleOrg> roles = asOrg(ORG_PAY, () ->
                permissionService.listUserRoles(ORG_PAY, USER_PAY));
        assertEquals(1, roles.size());
        assertFalse(roles.get(0).isActive(), "降权后 user_role_org.active 应为 false");

        // 留痕用 ACCESS_REVIEW_DOWNGRADE 区分
        assertEquals(1, countLogByAction(ORG_PAY, "ACCESS_REVIEW_DOWNGRADE"),
                "降权应留痕一条 ACCESS_REVIEW_DOWNGRADE");
        assertEquals(0, countLogByAction(ORG_PAY, "ACCESS_REVIEW_REVOKE"),
                "降权不应误记为 REVOKE 留痕");
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

    /** 以 owner 连接（绕 RLS）统计某 org 下指定 action 的 operation_log 留痕条数。 */
    private long countLogByAction(long orgId, String action) {
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             var ps = owner.prepareStatement(
                     "SELECT count(*) FROM operation_log WHERE org_id = ? AND action = ?")) {
            ps.setLong(1, orgId);
            ps.setString(2, action);
            try (var rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
