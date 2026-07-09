package com.mandao.grc;

import com.mandao.grc.common.isolation.IsolationContext;
import com.mandao.grc.modules.assessment.RiskLevel;
import com.mandao.grc.modules.audit.ChainVerifyResult;
import com.mandao.grc.modules.audit.HashChainService;
import com.mandao.grc.modules.vendor.VendorService;
import com.mandao.grc.modules.vendor.VendorStatus;
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
 * 第三方供应商集成测试（真实 PG + 切面 + RLS）。验证：
 *  1) 准入门控红线：未评估不得启用；评估后可启用；
 *  2) 监测状态机：暂停→恢复→终止，非法流转被拒；
 *  3) 组织隔离：org12 供应商，org13 看不到；
 *  4) 留痕：登记/评估/启用后哈希链校验通过且计数正确。
 *
 * 设计依据：需求 M·第三方供应商（准入/评估/监测）、D2-5。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "grc.scheduler.enabled=false")
@Testcontainers
class VendorTest {

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
    private VendorService vendorService;

    @Autowired
    private HashChainService hashChainService;

    private static final long ORG_PAY = 12L;
    private static final long ORG_CF = 13L;

    @BeforeEach
    void clean() throws Exception {
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement()) {
            s.executeUpdate("TRUNCATE vendor_incident, vendor_sla, vendor_assessment, vendor, operation_log RESTART IDENTITY CASCADE");
        }
    }

    @AfterEach
    void clearContext() {
        IsolationContext.clear();
    }

    @Test
    void M7深度_SLA跟踪_事件触发复评闭环_合规属性() {
        Long vid = asOrg(ORG_PAY, () -> vendorService.register(ORG_PAY, "V-SMS", "短信网关B",
                "通信", null, "MID", "c").getId());

        // 技术安全/DPA 属性
        var v = asOrg(ORG_PAY, () -> vendorService.updateCompliance(vid, "境内", true, "ISO27001,PCI DSS",
                true, false, "无再委托", "admin"));
        org.junit.jupiter.api.Assertions.assertTrue(v.isPciScope());
        org.junit.jupiter.api.Assertions.assertEquals("境内", v.getDataResidency());

        // SLA：新增 + 回填不达标
        var sla = asOrg(ORG_PAY, () -> vendorService.addSla(vid, "到达率", "≥98%", "99.1%",
                java.time.LocalDate.of(2026, 9, 1), true, "ops"));
        var tracked = asOrg(ORG_PAY, () -> vendorService.trackSla(sla.getId(), "96.7%", false, "ops"));
        org.junit.jupiter.api.Assertions.assertFalse(tracked.isMet(), "回填后应不达标");
        org.junit.jupiter.api.Assertions.assertEquals(1, asOrg(ORG_PAY, () -> vendorService.listAllSla()).size());

        // 事件触发复评：登记(OPEN)→复评(REASSESSING, EVENT 评估)→闭环(CLOSED)
        var inc = asOrg(ORG_PAY, () -> vendorService.reportIncident(vid, "被曝数据泄露", "媒体", "HIGH", "risk"));
        org.junit.jupiter.api.Assertions.assertEquals("OPEN", inc.getStatus());
        var re = asOrg(ORG_PAY, () -> vendorService.triggerReassess(inc.getId(), RiskLevel.HIGH, 55, "复评：高风险", "assessor"));
        org.junit.jupiter.api.Assertions.assertEquals("REASSESSING", re.getStatus());
        // EVENT 评估已登记且回写供应商风险
        var assessments = asOrg(ORG_PAY, () -> vendorService.listAssessments(vid));
        org.junit.jupiter.api.Assertions.assertTrue(assessments.stream().anyMatch(a -> "EVENT".equals(a.getAssessType())), "应有 EVENT 类评估");
        var closed = asOrg(ORG_PAY, () -> vendorService.closeIncident(inc.getId(), "risk"));
        org.junit.jupiter.api.Assertions.assertEquals("CLOSED", closed.getStatus());
        // OPEN 才能复评
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> runAsOrg(ORG_PAY, () -> vendorService.triggerReassess(inc.getId(), RiskLevel.MID, 70, "x", "a")));
    }

    @Test
    void 准入门控_未评估不得启用_评估后可启用并可监测() {
        Long vid = asOrg(ORG_PAY, () -> vendorService.register(ORG_PAY, "V-CLOUD", "某云服务商",
                "云服务", "ops@x.com", "关键", "c").getId());

        // 红线：未评估直接启用被拒
        assertThrows(IllegalStateException.class,
                () -> runAsOrg(ORG_PAY, () -> vendorService.activate(vid, "admin")));

        // 评估（高风险）→ 回写风险等级
        assertEquals(RiskLevel.HIGH,
                asOrg(ORG_PAY, () -> vendorService.assess(vid, RiskLevel.HIGH, 62, "数据出境风险偏高", "assessor").getRiskLevel()));

        // 评估后可启用
        assertEquals(VendorStatus.ACTIVE, asOrg(ORG_PAY, () -> vendorService.activate(vid, "admin").getStatus()));

        // 监测：暂停 → 恢复 → 终止
        assertEquals(VendorStatus.SUSPENDED, asOrg(ORG_PAY, () -> vendorService.suspend(vid, "SLA 异常", "ops").getStatus()));
        assertEquals(VendorStatus.ACTIVE, asOrg(ORG_PAY, () -> vendorService.reactivate(vid, "ops").getStatus()));
        assertEquals(VendorStatus.TERMINATED, asOrg(ORG_PAY, () -> vendorService.terminate(vid, "合同到期", "admin").getStatus()));
        // 终止后不可再暂停
        assertThrows(IllegalStateException.class,
                () -> runAsOrg(ORG_PAY, () -> vendorService.suspend(vid, "x", "ops")));
    }

    @Test
    void 评估过程留存_评估依据与原件附件() {
        Long vid = asOrg(ORG_PAY, () -> vendorService.register(ORG_PAY, "V-DOC", "某数据服务商",
                "数据处理", null, "关键", "c").getId());
        // 带评估依据的评估（记录"通过什么评估表单/维度得出结果"）
        var a = asOrg(ORG_PAY, () -> vendorService.assess(vid, RiskLevel.MID, 75,
                "总体可控", "ANNUAL", "依据《第三方安全评估表》五维：资质/数据安全/连续性/合规/事件史，各维评分见附件", "assessor"));
        assertEquals("依据《第三方安全评估表》五维：资质/数据安全/连续性/合规/事件史，各维评分见附件", a.getBasis());

        // 上传评估表单/报告原件 → sha256 固化
        byte[] doc = "第三方安全评估表\n资质:合格\n数据安全:良好".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var withDoc = asOrg(ORG_PAY, () -> vendorService.uploadAssessmentDoc(a.getId(), "供应商评估表.txt", doc, "assessor"));
        assertEquals("供应商评估表.txt", withDoc.getDocName());
        assertTrue(withDoc.getDocSha256() != null && withDoc.getDocSha256().length() == 64, "应 sha256 固化");
        assertTrue(asOrg(ORG_PAY, () -> vendorService.getAssessmentWithDoc(a.getId())).getDocBytes().length == doc.length);

        // 留痕：登记 + 评估 + 上传原件 = 3 条
        ChainVerifyResult r = asOrg(ORG_PAY, () -> hashChainService.verify(ORG_PAY));
        assertTrue(r.valid());
        assertEquals(3, r.count(), "供应商登记 + 评估 + 上传评估原件 = 3 条留痕");
    }

    @Test
    void 组织隔离_org12供应商org13看不到() {
        asOrg(ORG_PAY, () -> vendorService.register(ORG_PAY, "V-X", "仅支付可见", "外包", null, "一般", "c"));
        assertEquals(1, asOrg(ORG_PAY, () -> vendorService.list()).size(), "org12 应看到自己的 1 个供应商");
        assertTrue(asOrg(ORG_CF, () -> vendorService.list()).isEmpty(), "org13 不应看到 org12 的供应商");
    }

    @Test
    void 留痕_登记评估启用共3条() {
        Long vid = asOrg(ORG_PAY, () -> vendorService.register(ORG_PAY, "V-H", "H", "云", null, "重要", "c").getId()); // REGISTER
        asOrg(ORG_PAY, () -> vendorService.assess(vid, RiskLevel.LOW, 88, "良好", "a"));                                // ASSESS
        asOrg(ORG_PAY, () -> vendorService.activate(vid, "admin"));                                                    // ACTIVATE

        ChainVerifyResult r = asOrg(ORG_PAY, () -> hashChainService.verify(ORG_PAY));
        assertTrue(r.valid(), "留痕后链应校验通过");
        assertEquals(3, r.count(), "应有 3 条供应商留痕");
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
