package com.mandao.grc;

import com.mandao.grc.common.isolation.IsolationContext;
import com.mandao.grc.modules.asset.Asset;
import com.mandao.grc.modules.asset.AssetClassification;
import com.mandao.grc.modules.asset.AssetService;
import com.mandao.grc.modules.asset.AssetStatus;
import com.mandao.grc.modules.audit.ChainVerifyResult;
import com.mandao.grc.modules.audit.HashChainService;
import com.mandao.grc.modules.org.DuplicateOrgCodeException;
import com.mandao.grc.modules.org.OrgNode;
import com.mandao.grc.modules.org.OrgService;
import com.mandao.grc.modules.ropa.Ropa;
import com.mandao.grc.modules.ropa.RopaService;
import com.mandao.grc.modules.ropa.RopaStatus;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M6 组织与资产集成测试（真实 PG + 应用切面 + RLS）。验证：
 *  1) 组织树：在 org12（支付）下建子部门，path 正确（父path + '/' + 新id）；listTree 子树展开；code 唯一校验；
 *  2) 资产台账（含资产合规属性 CR-002）：登记含 PI/跨境/CHD/等保备案的资产，并可按合规属性筛查；ACTIVE→RETIRED；
 *  3) ROPA 生命周期：DRAFT→ACTIVE→RETIRED，非法流转被拒；
 *  4) 组织隔离：org12 的资产，org13 看不到（asset 表有 RLS）；
 *  5) 留痕：流转后对应 org 哈希链 verify 通过且条数正确。
 *
 * 注：org 表【无 RLS】（组织字典），故组织隔离用例落在【有 RLS 的 asset 表】上验证。
 * scheduler.enabled=false 关闭定时器，确保确定性。
 * 设计依据：需求文档 M6、CR-002、D1-2、D2-5。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "grc.scheduler.enabled=false")
@Testcontainers
class OrgAssetTest {

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
    private OrgService orgService;

    @Autowired
    private AssetService assetService;

    @Autowired
    private RopaService ropaService;

    @Autowired
    private HashChainService hashChainService;

    private static final long ORG_GROUP = 1L;   // 集团总部（GROUP，可见全树）
    private static final long ORG_PAY = 12L;    // 支付子公司
    private static final long ORG_CF = 13L;     // 消费金融

    /**
     * 每用例前清空 M6 业务表 + 操作日志，并删除测试新建的子组织（id>13），
     * 复位 org 到 V1 种子三行。owner 连接绕 RLS；asset/ropa FK 引用 org，用 CASCADE。
     */
    @BeforeEach
    void clean() throws Exception {
        execAsOwner("TRUNCATE asset, ropa, operation_log RESTART IDENTITY CASCADE");
        execAsOwner("DELETE FROM org WHERE id > 13");
    }

    @AfterEach
    void clearContext() {
        IsolationContext.clear();
    }

    // ---------- 组织树 ----------

    @Test
    void 建子组织_path为父路径加新id() {
        // 在 org12（支付，path '/1/12'）下建一个子部门
        OrgNode dept = asOrg(ORG_GROUP, () ->
                orgService.createSubOrg(ORG_PAY, "PAY-RISK", "支付风险部", "DEPT", "admin"));

        assertNotNull(dept.id());
        assertEquals(ORG_PAY, dept.parentId());
        assertEquals("/1/12/" + dept.id(), dept.path(), "子组织 path 应为 父path + '/' + 新id");

        // 子树展开：org12 子树应含其自身 + 新建子部门
        List<OrgNode> subtree = asOrg(ORG_GROUP, () -> orgService.listTree(ORG_PAY));
        assertEquals(2, subtree.size(), "org12 子树应含自身 + 1 个新建子部门");
        assertTrue(subtree.stream().anyMatch(n -> n.id().equals(dept.id())));
    }

    @Test
    void 建子组织_code重复被拒() {
        asOrg(ORG_GROUP, () -> orgService.createSubOrg(ORG_PAY, "DUP", "部门A", "DEPT", "admin"));
        // 复用同一 code → 唯一校验拒绝
        assertThrows(DuplicateOrgCodeException.class,
                () -> runAsOrg(ORG_GROUP, () -> orgService.createSubOrg(ORG_PAY, "DUP", "部门B", "DEPT", "admin")));
    }

    @Test
    void 手动配置_重命名与删除叶子() {
        OrgNode dept = asOrg(ORG_GROUP, () ->
                orgService.createSubOrg(ORG_PAY, "PAY-OPS", "运营部", "DEPT", "admin"));
        // 重命名
        OrgNode renamed = asOrg(ORG_GROUP, () -> orgService.rename(dept.id(), "运营管理部", "admin"));
        assertEquals("运营管理部", renamed.name());
        assertEquals("运营管理部", asOrg(ORG_GROUP, () -> orgService.get(dept.id())).name());
        // 删除叶子
        asOrg(ORG_GROUP, () -> { orgService.delete(dept.id(), "admin"); return null; });
        assertNull(asOrg(ORG_GROUP, () -> orgService.get(dept.id())), "删除后应查不到");
    }

    @Test
    void 删除门控_有子组织不可删_根不可删() {
        // 给 org12 建一个子部门 → org12 此时有子组织
        asOrg(ORG_GROUP, () -> orgService.createSubOrg(ORG_PAY, "PAY-MID", "中台", "DEPT", "admin"));
        // 删 org12（有子组织）→ 门控拦截（在留痕前抛出）
        assertThrows(IllegalStateException.class,
                () -> runAsOrg(ORG_GROUP, () -> { orgService.delete(ORG_PAY, "admin"); return null; }));
        // 删根组织 → 门控拦截
        assertThrows(IllegalStateException.class,
                () -> runAsOrg(ORG_GROUP, () -> { orgService.delete(ORG_GROUP, "admin"); return null; }));
    }

    // ---------- 资产台账 + 合规属性筛查 ----------

    @Test
    void 资产登记_含合规属性且可按属性筛查() {
        // 含 PI + 跨境 + CHD + 等保备案 的高敏资产
        asOrg(ORG_PAY, () -> assetService.register(ORG_PAY, "核心支付库", "DATABASE", "dba",
                AssetClassification.SENSITIVE, true, true, true, true, "CRITICAL", "admin"));
        // 一个普通内部资产（无任何合规标记）
        asOrg(ORG_PAY, () -> assetService.register(ORG_PAY, "内网门户", "APP", "ops",
                AssetClassification.INTERNAL, false, false, false, false, "LOW", "admin"));

        assertEquals(2, asOrg(ORG_PAY, () -> assetService.list()).size());

        // 按合规属性筛查（仅命中第一条）
        assertEquals(1, asOrg(ORG_PAY, () -> assetService.listContainingPi()).size(), "含PI资产应为1");
        assertEquals(1, asOrg(ORG_PAY, () -> assetService.listCrossBorder()).size(), "跨境资产应为1");
        assertEquals(1, asOrg(ORG_PAY, () -> assetService.listContainingChd()).size(), "含CHD资产应为1");
        assertEquals(1, asOrg(ORG_PAY, () -> assetService.listMlpsFiled()).size(), "等保备案资产应为1");
        assertEquals(1, asOrg(ORG_PAY, () ->
                assetService.listByClassification(AssetClassification.SENSITIVE)).size(), "敏感资产应为1");
    }

    @Test
    void 资产生命周期_停用全程通过() {
        Long id = asOrg(ORG_PAY, () -> assetService.register(ORG_PAY, "下线系统", "SYSTEM", "ops",
                AssetClassification.INTERNAL, false, false, false, false, "LOW", "admin").getId());
        assertEquals(AssetStatus.RETIRED, asOrg(ORG_PAY, () -> assetService.retire(id, "admin").getStatus()));
        // 已停用资产不可再编辑
        assertThrows(IllegalStateException.class, () -> runAsOrg(ORG_PAY, () ->
                assetService.update(id, "改名", "SYSTEM", "ops",
                        AssetClassification.INTERNAL, false, false, false, false, "LOW", "admin")));
    }

    // ---------- ROPA 生命周期 ----------

    @Test
    void ropa生命周期_草稿生效退役全程通过() {
        Long id = asOrg(ORG_PAY, () -> ropaService.create(ORG_PAY, "用户实名认证", "KYC合规",
                "姓名,身份证号,人脸", "法定义务", true, "5年", "dpo").getId());

        assertEquals(RopaStatus.ACTIVE, asOrg(ORG_PAY, () -> ropaService.activate(id, "dpo").getStatus()));
        assertEquals(RopaStatus.RETIRED, asOrg(ORG_PAY, () -> ropaService.retire(id, "dpo").getStatus()));
    }

    @Test
    void ropa非法流转_草稿直接退役被拒() {
        Long id = asOrg(ORG_PAY, () -> ropaService.create(ORG_PAY, "X", "p",
                "c", "同意", false, "1年", "dpo").getId());
        // DRAFT 不可直接 retire（retire 仅允许从 ACTIVE）
        assertThrows(IllegalStateException.class,
                () -> runAsOrg(ORG_PAY, () -> ropaService.retire(id, "dpo")));
    }

    // ---------- 组织隔离（asset 表有 RLS） ----------

    @Test
    void 组织隔离_org12的资产org13看不到() {
        asOrg(ORG_PAY, () -> assetService.register(ORG_PAY, "仅支付可见", "SYSTEM", "ops",
                AssetClassification.INTERNAL, false, false, false, false, "LOW", "admin"));

        List<Asset> payView = asOrg(ORG_PAY, () -> assetService.list());
        assertEquals(1, payView.size(), "org12 应看到自己的 1 个资产");

        List<Asset> cfView = asOrg(ORG_CF, () -> assetService.list());
        assertTrue(cfView.isEmpty(), "org13 不应看到 org12 的资产");
    }

    // ---------- 留痕 ----------

    @Test
    void 留痕_资产流转后哈希链校验通过且有记录() {
        Long id = asOrg(ORG_PAY, () -> assetService.register(ORG_PAY, "留痕资产", "SYSTEM", "ops",
                AssetClassification.INTERNAL, true, false, false, false, "MEDIUM", "a").getId()); // REGISTER=1
        asOrg(ORG_PAY, () -> assetService.update(id, "留痕资产", "SYSTEM", "ops",
                AssetClassification.SENSITIVE, true, true, false, false, "HIGH", "a"));            // UPDATE=2
        asOrg(ORG_PAY, () -> assetService.retire(id, "a"));                                        // RETIRE=3

        ChainVerifyResult r = asOrg(ORG_PAY, () -> hashChainService.verify(ORG_PAY));
        assertTrue(r.valid(), "留痕后链应校验通过");
        assertEquals(3, r.count(), "应有 3 条 M6 资产操作留痕");
    }

    @Test
    void 留痕_建子组织按父org分链() {
        // 建子组织按【父 org=12】分链留痕
        asOrg(ORG_GROUP, () -> orgService.createSubOrg(ORG_PAY, "AUDIT-DEPT", "审计部", "DEPT", "admin"));

        ChainVerifyResult r = asOrg(ORG_GROUP, () -> hashChainService.verify(ORG_PAY));
        assertTrue(r.valid(), "建子组织留痕后父 org 链应校验通过");
        assertEquals(1, r.count(), "应有 1 条 ORG_CREATE_SUB 留痕");
    }

    // ---------- 测试辅助 ----------

    private <T> T asOrg(long orgId, Supplier<T> action) {
        IsolationContext.set(visibleFor(orgId));
        try {
            return action.get();
        } finally {
            IsolationContext.clear();
        }
    }

    private void runAsOrg(long orgId, Callable<?> action) throws Exception {
        IsolationContext.set(visibleFor(orgId));
        try {
            action.call();
        } finally {
            IsolationContext.clear();
        }
    }

    /**
     * 模拟可见域：集团（org1）可见全树（1,12,13 + 测试新建子组织也在 '/1' 子树内），
     * 子公司仅见自身。建子组织/留痕需父 org 可见，故 GROUP 上下文用全可见集。
     */
    private List<Long> visibleFor(long orgId) {
        if (orgId == ORG_GROUP) {
            return List.of(ORG_GROUP, ORG_PAY, ORG_CF);
        }
        return List.of(orgId);
    }

    private void execAsOwner(String sql) throws Exception {
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement()) {
            s.executeUpdate(sql);
        }
    }
}
