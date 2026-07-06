package com.mandao.grc;

import com.mandao.grc.common.isolation.IsolationContext;
import com.mandao.grc.kernel.NotifyRuleEngine;
import com.mandao.grc.modules.assessment.Assessment;
import com.mandao.grc.modules.assessment.AssessmentService;
import com.mandao.grc.modules.assessment.AssessmentTemplate;
import com.mandao.grc.modules.assessment.RiskFindingService;
import com.mandao.grc.modules.assessment.RiskLevel;
import com.mandao.grc.modules.assessment.TemplateService;
import com.mandao.grc.modules.control.ControlFramework;
import com.mandao.grc.modules.policy.Policy;
import com.mandao.grc.modules.policy.PolicyService;
import com.mandao.grc.modules.regulation.ChangeType;
import com.mandao.grc.modules.regulation.Regulation;
import com.mandao.grc.modules.regulation.RegulationPolicyMap;
import com.mandao.grc.modules.regulation.RegulationService;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
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

import java.io.ByteArrayOutputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UAT 六轮批次集成测试。验证：
 *  1) 模板删除口径：自建可删；平台内置（owner=platform）不可删；被评估引用不可删；
 *  2) 制度全文（docx 上传提取）+ AI 符合度评估落库 + 法规变更置需重评（stale）；
 *  3) 通知规则引擎：整改逾期规则真值评估、变量渲染、幂等（重复评估不重复告警）；
 *  4) 风险登记册：跨评估聚合携来源评估标题。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "grc.scheduler.enabled=false")
@Testcontainers
class Uat6BatchTest {

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
    private TemplateService templateService;
    @Autowired
    private AssessmentService assessmentService;
    @Autowired
    private RiskFindingService riskFindingService;
    @Autowired
    private PolicyService policyService;
    @Autowired
    private RegulationService regulationService;
    @Autowired
    private NotifyRuleEngine notifyRuleEngine;

    private static final long ORG_PAY = 12L;

    @BeforeEach
    void clean() throws Exception {
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement()) {
            s.executeUpdate("TRUNCATE risk_treatment, risk_finding, assessment_answer, assessment_asset, "
                    + "assessment_doc CASCADE");
            s.executeUpdate("DELETE FROM assessment WHERE org_id = 12");
            s.executeUpdate("TRUNCATE regulation_policy_map, regulation_change CASCADE");
            s.executeUpdate("DELETE FROM regulation WHERE org_id = 12");
            s.executeUpdate("DELETE FROM policy_version WHERE org_id = 12");
            s.executeUpdate("DELETE FROM policy WHERE org_id = 12");
            s.executeUpdate("TRUNCATE remediation_order CASCADE");
            s.executeUpdate("DELETE FROM reminder_dispatch_log WHERE event_type LIKE 'RULE_%'");
        }
    }

    @AfterEach
    void clearContext() {
        IsolationContext.clear();
    }

    @Test
    void 模板删除口径_自建可删_内置与被引用不可删() {
        // 自建模板：可物理删除
        AssessmentTemplate mine = asOrg(ORG_PAY, () -> templateService.create(ORG_PAY, "TPL-DEL-1", "删测模板",
                ControlFramework.MLPS, "d", "tester", "c"));
        asOrg(ORG_PAY, () -> {
            templateService.delete(mine.getId(), "c");
            return null;
        });
        assertThrows(IllegalArgumentException.class,
                () -> asOrg(ORG_PAY, () -> templateService.get(mine.getId())), "删除后应不可见");

        // 平台内置（owner=platform）：不可删除
        AssessmentTemplate builtin = asOrg(1L, () -> templateService.list().stream()
                .filter(t -> "platform".equals(t.getOwner())).findFirst().orElseThrow());
        assertThrows(IllegalStateException.class, () -> asOrg(1L, () -> {
            templateService.delete(builtin.getId(), "c");
            return null;
        }), "平台内置模板不可删除");

        // 被评估引用：不可删除
        AssessmentTemplate used = asOrg(ORG_PAY, () -> templateService.create(ORG_PAY, "TPL-DEL-2", "被引用模板",
                ControlFramework.MLPS, "d", "tester", "c"));
        asOrg(ORG_PAY, () -> assessmentService.create(ORG_PAY, "引用模板的评估", "u", "2026", used.getId(), "c"));
        assertThrows(IllegalStateException.class, () -> asOrg(ORG_PAY, () -> {
            templateService.delete(used.getId(), "c");
            return null;
        }), "被评估引用的模板不可删除");
    }

    @Test
    void 制度全文上传_符合度评估落库_法规变更置需重评() throws Exception {
        Policy policy = asOrg(ORG_PAY, () -> policyService.create(ORG_PAY, "POL-CMP", "数据安全管理办法", null, "c"));

        // 未上传全文 → 评估被拒
        Regulation reg = asOrg(ORG_PAY, () -> regulationService.create(ORG_PAY, "REG-CMP", "网络数据安全管理条例",
                "国务院", "数据安全", LocalDate.of(2025, 1, 1), "对数据处理者提出分类分级、出境评估等要求", "c"));
        RegulationPolicyMap map = asOrg(ORG_PAY, () ->
                regulationService.addMap(reg.getId(), policy.getId(), "§12", "留存期限条款", "c"));
        assertThrows(IllegalStateException.class,
                () -> asOrg(ORG_PAY, () -> regulationService.assessCompliance(map.getId(), "c")),
                "无全文时应拒绝评估并提示上传");

        // 上传 docx → 提取全文 + sha256 固化
        byte[] docx = buildDocx("第一条 为规范数据处理活动，保障数据安全，制定本办法。第二条 数据分类分级按国家标准执行。");
        Policy uploaded = asOrg(ORG_PAY, () ->
                policyService.uploadDocument(policy.getId(), "数据安全管理办法.docx", docx, "c"));
        assertTrue(uploaded.getContent() != null && uploaded.getContent().contains("数据分类分级"), "应提取到全文");
        assertNotNull(uploaded.getDocSha256(), "原件应 sha256 固化");

        // AI 符合度评估落库（本地离线 Provider 下结论允许为 待复核，但详情与时间必须落库）
        RegulationPolicyMap assessed = asOrg(ORG_PAY, () -> regulationService.assessCompliance(map.getId(), "c"));
        assertNotNull(assessed.getAssessVerdict(), "应归类出结论");
        assertTrue(List.of("符合", "部分符合", "不符合", "待复核").contains(assessed.getAssessVerdict()));
        assertTrue(assessed.getAssessDetail() != null && !assessed.getAssessDetail().isBlank(), "评估详情应落库");
        assertNotNull(assessed.getAssessedAt(), "评估时间应落库");
        assertTrue(!assessed.isAssessStale(), "刚评完不应是需重评");

        // 法规再变更 → 既有评估置需重评
        asOrg(ORG_PAY, () -> regulationService.recordChange(reg.getId(), ChangeType.AMENDED,
                LocalDate.of(2026, 7, 1), "新增跨境数据流动负面清单条款", "c"));
        RegulationPolicyMap after = asOrg(ORG_PAY, () -> regulationService.listMaps(reg.getId()).get(0));
        assertTrue(after.isAssessStale(), "法规变更后应标记需重评");
    }

    @Test
    void 通知规则引擎_整改逾期告警_变量渲染_幂等() throws Exception {
        // 造一条逾期 5 天的整改单（owner 直插，绕过审计发现全链路，聚焦引擎行为）
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement()) {
            long planId;
            try (ResultSet rs = s.executeQuery(
                    "INSERT INTO audit_plan(org_id, title, plan_start_date, external_status) "
                            + "VALUES (12, '引擎测试计划', '2099-01-01', 'DONE') RETURNING id")) {
                rs.next();
                planId = rs.getLong(1);
            }
            long findingId;
            try (ResultSet rs = s.executeQuery(
                    "INSERT INTO audit_finding(org_id, audit_plan_id, title, severity) "
                            + "VALUES (12, " + planId + ", '引擎测试发现', 'HIGH') RETURNING id")) {
                rs.next();
                findingId = rs.getLong(1);
            }
            s.executeUpdate("INSERT INTO remediation_order(org_id, finding_id, assignee, due_date, measure) "
                    + "VALUES (12, " + findingId + ", '张三', current_date - 5, '整改逾期引擎验证')");
        }

        int produced = notifyRuleEngine.runOnce(LocalDate.now());
        assertTrue(produced >= 1, "至少应产出整改逾期告警，实际=" + produced);

        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement();
             ResultSet rs = s.executeQuery("SELECT count(*), min(message) FROM reminder_dispatch_log "
                     + "WHERE event_type = 'RULE_REMEDIATION_OVERDUE'")) {
            rs.next();
            assertEquals(1, rs.getInt(1), "同一整改单同一规则只告警一次");
            String msg = rs.getString(2);
            assertTrue(msg.contains("整改逾期引擎验证"), "消息应渲染 {标题} 变量：" + msg);
            assertTrue(msg.contains("已逾期 5 天"), "消息应渲染 {逾期天数} 变量：" + msg);
            assertTrue(msg.contains("张三"), "消息应渲染 {责任人} 变量：" + msg);
        }

        // 幂等：再评估一轮，不重复告警
        notifyRuleEngine.runOnce(LocalDate.now());
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement();
             ResultSet rs = s.executeQuery("SELECT count(*) FROM reminder_dispatch_log "
                     + "WHERE event_type = 'RULE_REMEDIATION_OVERDUE'")) {
            rs.next();
            assertEquals(1, rs.getInt(1), "重复评估不得重复告警（幂等台账）");
        }
    }

    @Test
    void b35_法规采集同批合并为一条摘要() throws Exception {
        // 造 3 条今日采集的法规（同批），B35 应合并为一条摘要而非 3 条提醒
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement()) {
            s.executeUpdate("DELETE FROM regulation_crawled WHERE org_id = 12");
            long srcId;
            try (ResultSet rs = s.executeQuery("INSERT INTO regulation_source(org_id, name, source_type, frequency) "
                    + "VALUES (12, 'B35源', 'SAMPLE', 'DAILY') RETURNING id")) {
                rs.next();
                srcId = rs.getLong(1);
            }
            for (int i = 1; i <= 3; i++) {
                s.executeUpdate("INSERT INTO regulation_crawled(org_id, source_id, title, issuer, dedup_key, fetched_at) "
                        + "VALUES (12, " + srcId + ", '新规" + i + "', '人民银行', 'b35-key-" + i + "', now())");
            }
        }

        notifyRuleEngine.runOnce(LocalDate.now());

        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement();
             ResultSet rs = s.executeQuery("SELECT count(*), min(message) FROM reminder_dispatch_log "
                     + "WHERE event_type = 'RULE_REG_NEW' AND object_type = 'REG_CRAWLED_BATCH' AND org_id = 12")) {
            rs.next();
            assertEquals(1, rs.getInt(1), "3 条同批采集应合并为 1 条摘要提醒");
            assertTrue(rs.getString(2).contains("本次新增 3 条"), "摘要应含条数：" + rs.getString(2));
        }

        // 幂等：同日再扫不重复产
        notifyRuleEngine.runOnce(LocalDate.now());
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement();
             ResultSet rs = s.executeQuery("SELECT count(*) FROM reminder_dispatch_log "
                     + "WHERE event_type = 'RULE_REG_NEW' AND org_id = 12")) {
            rs.next();
            assertEquals(1, rs.getInt(1), "同日重复扫描摘要幂等");
        }
    }

    @Test
    void 风险登记册_跨评估聚合携来源标题() {
        Assessment a = asOrg(ORG_PAY, () -> assessmentService.create(ORG_PAY, "登记册来源评估", "u", "2026", "c"));
        asOrg(ORG_PAY, () -> riskFindingService.createFinding(ORG_PAY, a.getId(), "登记册测试风险",
                RiskLevel.HIGH, "c"));

        List<RiskFindingService.RegisterRow> rows = asOrg(ORG_PAY, () -> riskFindingService.registerRows());
        assertTrue(rows.stream().anyMatch(r -> "登记册测试风险".equals(r.title())
                        && "登记册来源评估".equals(r.assessmentTitle())
                        && r.inherentLevel() == RiskLevel.HIGH),
                "登记册应聚合发现并携来源评估标题");
    }

    /** 构造一份最小 docx（单段落）。 */
    private static byte[] buildDocx(String text) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.createParagraph().createRun().setText(text);
            doc.write(out);
            return out.toByteArray();
        }
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
