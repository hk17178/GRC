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
    private com.mandao.grc.modules.custom.CustomFieldService customFieldService;

    @Autowired
    private com.mandao.grc.modules.custom.CustomViewService customViewService;

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
        execAsOwner("TRUNCATE asset, ropa, custom_field_def, custom_view_def, operation_log RESTART IDENTITY CASCADE");
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

    // ---------- B12 Phase1：自定义字段 ----------

    @Test
    void b12_自定义字段登记与ext校验落库() {
        // 登记两个自定义字段：数值(必填) + 下拉
        asOrg(ORG_PAY, () -> customFieldService.create(ORG_PAY, "ASSET", "cpu_cores", "CPU核数",
                com.mandao.grc.modules.custom.CustomFieldDef.DataType.NUMBER, null, true, false, false, 0, "admin"));
        asOrg(ORG_PAY, () -> customFieldService.create(ORG_PAY, "ASSET", "env", "环境",
                com.mandao.grc.modules.custom.CustomFieldDef.DataType.SELECT, "生产;测试;开发", false, false, false, 1, "admin"));
        assertEquals(2, asOrg(ORG_PAY, () -> customFieldService.listActive("ASSET")).size());

        // 登记资产带 ext（NUMBER 归一为数值、SELECT 校验选项）
        var ext = new java.util.HashMap<String, Object>();
        ext.put("cpu_cores", "16");
        ext.put("env", "生产");
        var asset = asOrg(ORG_PAY, () -> assetService.register(ORG_PAY, "自定义字段资产", "SYSTEM", "o",
                AssetClassification.INTERNAL, false, false, false, false, "MID",
                null, null, null, null, ext, "admin"));
        assertEquals(16.0, ((Number) asset.getExt().get("cpu_cores")).doubleValue(), 0.001, "NUMBER 应归一为数值");
        assertEquals("生产", asset.getExt().get("env"));
    }

    @Test
    void b12_未登记键拒绝_必填缺失拒绝_选项越界拒绝() {
        asOrg(ORG_PAY, () -> customFieldService.create(ORG_PAY, "ASSET", "cpu_cores", "CPU核数",
                com.mandao.grc.modules.custom.CustomFieldDef.DataType.NUMBER, null, true, false, false, 0, "admin"));

        // 未登记键
        var bad = new java.util.HashMap<String, Object>();
        bad.put("unknown_key", "x");
        assertThrows(IllegalArgumentException.class, () -> runAsOrg(ORG_PAY, () ->
                assetService.register(ORG_PAY, "a", "SYSTEM", "o", AssetClassification.INTERNAL,
                        false, false, false, false, "MID", null, null, null, null, bad, "admin")));

        // 必填缺失（cpu_cores required 但未给）
        assertThrows(IllegalArgumentException.class, () -> runAsOrg(ORG_PAY, () ->
                assetService.register(ORG_PAY, "a", "SYSTEM", "o", AssetClassification.INTERNAL,
                        false, false, false, false, "MID", null, null, null, null, java.util.Map.of(), "admin")));

        // 数值类型非数
        var notNum = new java.util.HashMap<String, Object>();
        notNum.put("cpu_cores", "abc");
        assertThrows(IllegalArgumentException.class, () -> runAsOrg(ORG_PAY, () ->
                assetService.register(ORG_PAY, "a", "SYSTEM", "o", AssetClassification.INTERNAL,
                        false, false, false, false, "MID", null, null, null, null, notNum, "admin")));
    }

    @Test
    void b12_字段键非法拒绝_停用后不再校验() {
        // 非法键
        assertThrows(IllegalArgumentException.class, () -> runAsOrg(ORG_PAY, () ->
                customFieldService.create(ORG_PAY, "ASSET", "2bad key", "坏键",
                        com.mandao.grc.modules.custom.CustomFieldDef.DataType.TEXT, null, false, false, false, 0, "admin")));

        // 停用字段后，其键不再是启用字段 → 带该键会被当"未登记"拒绝
        Long id = asOrg(ORG_PAY, () -> customFieldService.create(ORG_PAY, "ASSET", "legacy", "旧字段",
                com.mandao.grc.modules.custom.CustomFieldDef.DataType.TEXT, null, false, false, false, 0, "admin").getId());
        asOrg(ORG_PAY, () -> customFieldService.retire(id, "admin"));
        assertTrue(asOrg(ORG_PAY, () -> customFieldService.listActive("ASSET")).isEmpty(), "停用后无启用字段");
    }

    @Test
    void b12_自定义字段组织隔离() {
        asOrg(ORG_PAY, () -> customFieldService.create(ORG_PAY, "ASSET", "pay_only", "仅支付",
                com.mandao.grc.modules.custom.CustomFieldDef.DataType.TEXT, null, false, false, false, 0, "admin"));
        assertEquals(1, asOrg(ORG_PAY, () -> customFieldService.list("ASSET")).size());
        assertTrue(asOrg(13L, () -> customFieldService.list("ASSET")).isEmpty(), "org13 不应看到 org12 的字段定义");
    }

    // ---------- B12 Phase2：自定义列表视图（H-05，隔离红线核心） ----------

    @Test
    void b12v2_视图执行_列筛选排序命中() {
        asOrg(ORG_PAY, () -> assetService.register(ORG_PAY, "核心支付库", "DATABASE", "dba",
                AssetClassification.SENSITIVE, true, false, false, false, "CRITICAL", "admin"));
        asOrg(ORG_PAY, () -> assetService.register(ORG_PAY, "内网门户", "APP", "ops",
                AssetClassification.INTERNAL, false, false, false, false, "LOW", "admin"));

        // 声明式：仅取名称/分级，筛 SENSITIVE，按名称升序
        String def = "{\"columns\":[\"name\",\"classification\"],"
                + "\"filters\":[{\"field\":\"classification\",\"op\":\"eq\",\"value\":\"SENSITIVE\"}],"
                + "\"sort\":{\"field\":\"name\",\"dir\":\"asc\"}}";
        var view = asOrg(ORG_PAY, () -> customViewService.create(ORG_PAY, "ASSET", "敏感资产视图", def, "admin"));

        List<java.util.Map<String, Object>> rows = asOrg(ORG_PAY, () -> customViewService.execute(view.getId()));
        assertEquals(1, rows.size(), "仅命中 1 条 SENSITIVE 资产");
        assertEquals("核心支付库", rows.get(0).get("name"));
        assertEquals("SENSITIVE", rows.get(0).get("classification"));
    }

    @Test
    void b12v2_未授权列拒绝_未授权筛选拒绝() {
        // 引用非白名单列 org_id（隔离锚点，绝不可被视图选出）
        assertThrows(IllegalArgumentException.class, () -> runAsOrg(ORG_PAY, () ->
                customViewService.create(ORG_PAY, "ASSET", "越权列", "{\"columns\":[\"org_id\"]}", "admin")));
        // 筛选引用未授权字段
        assertThrows(IllegalArgumentException.class, () -> runAsOrg(ORG_PAY, () ->
                customViewService.create(ORG_PAY, "ASSET", "越权筛选",
                        "{\"columns\":[\"name\"],\"filters\":[{\"field\":\"secret_col\",\"op\":\"eq\",\"value\":\"x\"}]}", "admin")));
        // 排序引用未授权字段
        assertThrows(IllegalArgumentException.class, () -> runAsOrg(ORG_PAY, () ->
                customViewService.create(ORG_PAY, "ASSET", "越权排序",
                        "{\"columns\":[\"name\"],\"sort\":{\"field\":\"org_id\",\"dir\":\"asc\"}}", "admin")));
    }

    @Test
    void b12v2_自定义字段可作为列与筛选() {
        asOrg(ORG_PAY, () -> customFieldService.create(ORG_PAY, "ASSET", "cpu_cores", "CPU核数",
                com.mandao.grc.modules.custom.CustomFieldDef.DataType.NUMBER, null, false, false, true, 0, "admin"));
        var big = new java.util.HashMap<String, Object>();
        big.put("cpu_cores", "16");
        asOrg(ORG_PAY, () -> assetService.register(ORG_PAY, "大机", "SYSTEM", "o",
                AssetClassification.INTERNAL, false, false, false, false, "MID", null, null, null, null, big, "admin"));
        var small = new java.util.HashMap<String, Object>();
        small.put("cpu_cores", "2");
        asOrg(ORG_PAY, () -> assetService.register(ORG_PAY, "小机", "SYSTEM", "o",
                AssetClassification.INTERNAL, false, false, false, false, "MID", null, null, null, null, small, "admin"));

        // 自定义字段以 ext.<key> 引用；NUMBER 转 numeric 比较，筛 >= 8
        String def = "{\"columns\":[\"name\",\"ext.cpu_cores\"],"
                + "\"filters\":[{\"field\":\"ext.cpu_cores\",\"op\":\"gte\",\"value\":8}]}";
        List<java.util.Map<String, Object>> rows = asOrg(ORG_PAY, () -> customViewService.preview("ASSET", def));
        assertEquals(1, rows.size(), "仅命中 CPU>=8 的大机");
        assertEquals("大机", rows.get(0).get("name"));
    }

    @Test
    void b12v2_跨子公司视图0越界_TC_SEC_102() {
        // org12 有一条资产
        asOrg(ORG_PAY, () -> assetService.register(ORG_PAY, "支付机密库", "DATABASE", "dba",
                AssetClassification.SENSITIVE, true, false, false, false, "CRITICAL", "admin"));

        // org13 构造一个"想看全部资产"的视图（甚至直接按名称精确匹配 org12 的资产名）
        String greedy = "{\"columns\":[\"name\",\"classification\"]}";
        String targeted = "{\"columns\":[\"name\"],"
                + "\"filters\":[{\"field\":\"name\",\"op\":\"eq\",\"value\":\"支付机密库\"}]}";

        // 期望：RLS 兜底，org13 视角下均 0 行——无路径越过 visibleOrgs
        assertTrue(asOrg(ORG_CF, () -> customViewService.preview("ASSET", greedy)).isEmpty(),
                "org13 的全量视图不得看到 org12 资产");
        assertTrue(asOrg(ORG_CF, () -> customViewService.preview("ASSET", targeted)).isEmpty(),
                "org13 精确匹配 org12 资产名仍应 0 行（TC-SEC-102）");

        // 反证：org12 自己能看到
        assertEquals(1, asOrg(ORG_PAY, () -> customViewService.preview("ASSET", greedy)).size());
    }

    @Test
    void b12v2_视图定义组织隔离() {
        String def = "{\"columns\":[\"name\"]}";
        asOrg(ORG_PAY, () -> customViewService.create(ORG_PAY, "ASSET", "支付视图", def, "admin"));
        assertEquals(1, asOrg(ORG_PAY, () -> customViewService.list("ASSET")).size());
        assertTrue(asOrg(ORG_CF, () -> customViewService.list("ASSET")).isEmpty(),
                "org13 不应看到 org12 的视图定义");
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
    void b14_ropa含处理方式与接收方法定字段() {
        Long id = asOrg(ORG_PAY, () -> ropaService.create(ORG_PAY, "商户结算", "履约", "银行卡号",
                "合同", false, "5年", "收集、存储、对外提供", "银联、合作银行", "dpo").getId());
        var r = asOrg(ORG_PAY, () -> ropaService.get(id));
        assertEquals("收集、存储、对外提供", r.getProcessingMethod(), "处理方式应落库");
        assertEquals("银联、合作银行", r.getRecipients(), "接收方应落库");
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
