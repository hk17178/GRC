package com.mandao.grc;

import com.mandao.grc.common.isolation.IsolationContext;
import com.mandao.grc.kernel.AlertPushService;
import com.mandao.grc.modules.ai.KnowledgeBaseService;
import com.mandao.grc.modules.assessment.RiskFinding;
import com.mandao.grc.modules.assessment.RiskFindingService;
import com.mandao.grc.modules.assessment.RiskLevel;
import com.mandao.grc.modules.assessment.form.BuiltinRiskFormGenerator;
import com.mandao.grc.modules.assessment.form.DocxFormParser;
import com.mandao.grc.modules.assessment.form.FormSchema;
import com.mandao.grc.modules.asset.Asset;
import com.mandao.grc.modules.asset.AssetClassification;
import com.mandao.grc.modules.asset.AssetService;
import com.mandao.grc.modules.atv.AtvService;
import com.mandao.grc.modules.atv.Threat;
import com.mandao.grc.modules.atv.Vulnerability;
import com.mandao.grc.modules.obligation.Obligation;
import com.mandao.grc.modules.obligation.ObligationService;
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
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UAT 八轮批次集成测试（评估报告排期 8-1~8-12 的可测核心）。验证：
 *  1) 8-3 义务举证链：满足状态由链上对象派生（GAP → PARTIAL → MET），同对象防重；
 *  2) 8-11 风险直登：不挂评估直接入登记册，来源标注与「日常登记」展示口径；
 *  3) 8-10 ATV 三库治理：被场景引用的威胁不可删、被发现引用的场景不可删、编辑生效；
 *  4) 8-4 条款切片：第X条/章 边界切块（条款号保留在块首）；
 *  5) 8-1 通道外推：白名单拦截的失败发送也落留痕（notify_send_log）；
 *  6) 8-8 差异化表单：等保表单含符合性自查章节、PBOC 表单含重点领域章节。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "grc.scheduler.enabled=false")
@Testcontainers
class Uat8BatchTest {

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
    private ObligationService obligationService;
    @Autowired
    private RiskFindingService riskFindingService;
    @Autowired
    private AtvService atvService;
    @Autowired
    private AssetService assetService;
    @Autowired
    private AlertPushService alertPushService;
    @Autowired
    private BuiltinRiskFormGenerator formGenerator;
    @Autowired
    private DocxFormParser formParser;

    private static final long ORG_PAY = 12L;

    @BeforeEach
    void clean() throws Exception {
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement()) {
            s.executeUpdate("TRUNCATE obligation_link, notify_send_log CASCADE");
            s.executeUpdate("DELETE FROM obligation WHERE org_id = 12");
            s.executeUpdate("TRUNCATE risk_treatment, risk_finding, risk_scenario, threat, vulnerability, asset CASCADE");
            s.executeUpdate("DELETE FROM notify_config WHERE kind = 'CHANNEL'");
        }
    }

    @AfterEach
    void clearContext() {
        IsolationContext.clear();
    }

    @Test
    void 义务举证链_派生状态与防重() {
        Obligation o = asOrg(ORG_PAY, () -> obligationService.create(ORG_PAY, "OBL-8", "交易日志留存 5 年",
                "条例§41", "数据合规", "留存要求", "科技部", null, "c"));
        // 无链 → GAP
        assertEquals("GAP", derivedOf(o.getId()));
        // 挂制度依据 → PARTIAL（有依据无证据）
        asOrg(ORG_PAY, () -> obligationService.addLink(o.getId(), "POLICY", 1L, "对应制度第3章", "c"));
        assertEquals("PARTIAL", derivedOf(o.getId()));
        // 同对象防重
        assertThrows(IllegalStateException.class, () -> asOrg(ORG_PAY, () ->
                obligationService.addLink(o.getId(), "POLICY", 1L, "重复", "c")));
        // 再挂证据 → MET
        asOrg(ORG_PAY, () -> obligationService.addLink(o.getId(), "EVIDENCE", 9L, "留存配置截图", "c"));
        assertEquals("MET", derivedOf(o.getId()));
    }

    private String derivedOf(Long obligationId) {
        return asOrg(ORG_PAY, () -> obligationService.listWithDerived().stream()
                .filter(r -> r.obligation().getId().equals(obligationId))
                .findFirst().orElseThrow().derivedStatus());
    }

    @Test
    void 风险直登_入登记册携来源() {
        RiskFinding f = asOrg(ORG_PAY, () -> riskFindingService.createDirect(ORG_PAY,
                "生产库高危漏洞未修复", RiskLevel.HIGH, "VULN", "seculops"));
        assertEquals("VULN", f.getSource());
        var row = asOrg(ORG_PAY, () -> riskFindingService.registerRows().stream()
                .filter(r -> r.id().equals(f.getId())).findFirst().orElseThrow());
        assertEquals("日常登记", row.assessmentTitle(), "不挂评估的直登风险应显示「日常登记」");
        assertEquals("VULN", row.source());
    }

    @Test
    void ATV治理_引用校验与编辑() {
        Asset asset = asOrg(ORG_PAY, () -> assetService.register(ORG_PAY, "网关A", "SYSTEM", "o",
                AssetClassification.INTERNAL, false, false, false, false, "HIGH", "c"));
        Threat t = asOrg(ORG_PAY, () -> atvService.createThreat(ORG_PAY, "T8", "拒绝服务", "网络", null, "c"));
        Vulnerability v = asOrg(ORG_PAY, () -> atvService.createVulnerability(ORG_PAY, "V8", "无限流", "配置", null, "c"));
        var sc = asOrg(ORG_PAY, () -> atvService.createScenario(asset.getId(), t.getId(), v.getId(), 3, 3, "x", "c"));

        // 被场景引用的威胁不可删
        assertThrows(IllegalStateException.class, () -> asOrg(ORG_PAY, () -> {
            atvService.deleteThreat(t.getId(), "c");
            return null;
        }));
        // 编辑生效
        Threat updated = asOrg(ORG_PAY, () -> atvService.updateThreat(t.getId(), "分布式拒绝服务", "网络", "DDoS", "c"));
        assertEquals("分布式拒绝服务", updated.getName());
        // 未派生发现的场景可删
        asOrg(ORG_PAY, () -> {
            atvService.deleteScenario(sc.getId(), "c");
            return null;
        });
        // 场景删掉后威胁可删
        asOrg(ORG_PAY, () -> {
            atvService.deleteThreat(t.getId(), "c");
            return null;
        });
        assertTrue(asOrg(ORG_PAY, () -> atvService.listThreats().stream()
                .noneMatch(x -> x.getId().equals(t.getId()))));
    }

    @Test
    void 条款切片_按条边界切块() {
        String text = "第一条 为规范数据处理活动，制定本办法。\n"
                + "第二条 本办法适用于境内数据处理者。\n"
                + "第三条 数据分类分级按国家标准执行，重要数据实行目录管理。";
        List<String> chunks = KnowledgeBaseService.chunk(text);
        assertTrue(chunks.size() >= 3, "应按条款边界切出至少 3 块，实际=" + chunks.size());
        assertTrue(chunks.get(0).startsWith("第一条"), "块首应保留条款号：" + chunks.get(0));
        assertTrue(chunks.get(2).startsWith("第三条"), "块首应保留条款号：" + chunks.get(2));
    }

    @Test
    void 通道外推_白名单拦截也落发送留痕() throws Exception {
        // 造一条指向内网的企微通道（白名单外 → 发送必失败，但留痕必须有）
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement()) {
            s.executeUpdate("INSERT INTO notify_config(org_id, kind, name, detail, enabled) VALUES "
                    + "(1, 'CHANNEL', '测试机器人', "
                    + "'{\"type\":\"WECOM\",\"target\":\"http://127.0.0.1/hook\"}', TRUE)");
        }
        int ok = alertPushService.pushAll(List.of(new AlertPushService.Alert(ORG_PAY, "外推留痕验证告警")));
        assertEquals(0, ok, "白名单外目标不应发送成功");
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement();
             ResultSet rs = s.executeQuery("SELECT success, error, message FROM notify_send_log ORDER BY id DESC LIMIT 1")) {
            assertTrue(rs.next(), "应有发送留痕");
            assertFalse(rs.getBoolean(1), "留痕应记失败");
            assertTrue(rs.getString(2).contains("白名单"), "失败原因应说明白名单拦截：" + rs.getString(2));
            assertEquals("外推留痕验证告警", rs.getString(3));
        }
    }

    @Test
    void m10_多通道_邮件短信也被路由并留痕() throws Exception {
        // #61：登记邮件 + 短信通道（此前仅 WECOM 被外推，邮件/短信被忽略）
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement()) {
            s.executeUpdate("INSERT INTO notify_config(org_id, kind, name, detail, enabled) VALUES "
                    + "(1, 'CHANNEL', '邮件网关', '{\"type\":\"EMAIL\",\"target\":\"http://127.0.0.1/mail\",\"recipient\":\"a@b.com\"}', TRUE),"
                    + "(1, 'CHANNEL', '短信网关', '{\"type\":\"SMS\",\"target\":\"http://127.0.0.1/sms\",\"recipient\":\"13800000000\"}', TRUE)");
        }
        alertPushService.pushAll(List.of(new AlertPushService.Alert(ORG_PAY, "多通道验证")));
        // 两通道均产生留痕（channel_type=EMAIL/SMS）→ 证明已被 loadChannels 路由（内网目标发送失败，但留痕即达标）
        java.util.Set<String> types = new java.util.HashSet<>();
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement();
             ResultSet rs = s.executeQuery("SELECT channel_type FROM notify_send_log WHERE message = '多通道验证'")) {
            while (rs.next()) {
                types.add(rs.getString(1));
            }
        }
        assertTrue(types.contains("EMAIL") && types.contains("SMS"), "邮件+短信通道均应被路由并留痕：" + types);
    }

    @Test
    void 差异化表单_等保与PBOC章节() {
        FormSchema mlps = formParser.parse(formGenerator.generateFor("TPL-MLPS", "等保 "));
        assertTrue(mlps.sections().stream().anyMatch(sec -> sec.title().contains("等保符合性自查")),
                "等保表单应含符合性自查章节");
        assertTrue(mlps.sections().stream().anyMatch(sec -> sec.title().contains("资产识别")),
                "预填依赖的资产识别章节应保留");

        FormSchema pboc = formParser.parse(formGenerator.generateFor("TPL-PBOC", "PBOC "));
        assertTrue(pboc.sections().stream().anyMatch(sec -> sec.title().contains("重点领域自查")),
                "PBOC 表单应含重点领域自查章节");
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
