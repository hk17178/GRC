package com.mandao.grc;

import com.mandao.grc.common.isolation.IsolationContext;
import com.mandao.grc.kernel.EscalationRunner;
import com.mandao.grc.kernel.NotifyRuleEngine;
import com.mandao.grc.kernel.SceneNotifyConsumer;
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
    @Autowired
    private EscalationRunner escalationRunner;
    @Autowired
    private SceneNotifyConsumer sceneNotifyConsumer;

    private static final long ORG_PAY = 12L;
    private static final long ORG_CF = 13L;

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
            // §九 接线二：清运行态场景通知/升级留痕 + 测试装配的场景/升级链（notif_scene_def 全局预置不清）
            s.executeUpdate("TRUNCATE scene_escalation_log, scene_notification CASCADE");
            s.executeUpdate("DELETE FROM notification_escalation WHERE org_id IN (12, 13)");
            s.executeUpdate("DELETE FROM notification_scene WHERE org_id IN (12, 13)");
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

    // ---------- D 批：通知中心四类新数据源（REG_CHANGE / ACCOUNT_LOCKED / UAR_OVERDUE / COMPLIANCE_DIGEST）----------

    @Test
    void d1_法规变更影响预警_渲染变更类型与制度数_幂等() throws Exception {
        Regulation reg = asOrg(ORG_PAY, () -> regulationService.create(ORG_PAY, "REG-D1", "个人信息保护法",
                "全国人大", "数据安全", LocalDate.of(2026, 1, 1), "摘要", "c"));
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement()) {
            s.executeUpdate("INSERT INTO regulation_policy_map(org_id, regulation_id, policy_id, clause) "
                    + "VALUES (12, " + reg.getId() + ", 1, '§41')");
        }
        asOrg(ORG_PAY, () -> regulationService.recordChange(reg.getId(), ChangeType.AMENDED,
                LocalDate.now(), "新增跨境数据流动条款", "c"));

        notifyRuleEngine.runOnce(LocalDate.now());
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement();
             ResultSet rs = s.executeQuery("SELECT count(*), min(message) FROM reminder_dispatch_log "
                     + "WHERE event_type = 'RULE_REG_CHANGE'")) {
            rs.next();
            assertEquals(1, rs.getInt(1), "一条法规变更应产一条影响预警");
            String msg = rs.getString(2);
            assertTrue(msg.contains("个人信息保护法"), "应渲染 {标题}：" + msg);
            assertTrue(msg.contains("修订"), "应渲染中文 {变更类型}：" + msg);
            assertTrue(msg.contains("1 项关联制度"), "应渲染 {制度数}=1：" + msg);
        }
        // 幂等：同一变更再评估不重复告警
        notifyRuleEngine.runOnce(LocalDate.now());
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement();
             ResultSet rs = s.executeQuery("SELECT count(*) FROM reminder_dispatch_log WHERE event_type = 'RULE_REG_CHANGE'")) {
            rs.next();
            assertEquals(1, rs.getInt(1), "重复评估幂等");
        }
    }

    @Test
    void d3_账号锁定告警_渲染账号() throws Exception {
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement()) {
            s.executeUpdate("UPDATE app_user SET locked_until = now() + interval '15 minutes' WHERE username = 'group_admin'");
        }
        try {
            notifyRuleEngine.runOnce(LocalDate.now());
            try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
                 Statement s = owner.createStatement();
                 ResultSet rs = s.executeQuery("SELECT count(*) FROM reminder_dispatch_log "
                         + "WHERE event_type = 'RULE_ACCOUNT_LOCKED' AND message LIKE '%group_admin%'")) {
                rs.next();
                assertEquals(1, rs.getInt(1), "锁定账号应产一条 {账号} 告警");
            }
        } finally {
            try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
                 Statement s = owner.createStatement()) {
                s.executeUpdate("UPDATE app_user SET locked_until = NULL WHERE username = 'group_admin'");
            }
        }
    }

    @Test
    void d3_访问复核超期提醒_渲染周期与审阅人() throws Exception {
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement()) {
            s.executeUpdate("DELETE FROM access_review WHERE org_id IN (12, 13)");
            s.executeUpdate("INSERT INTO access_review(org_id, period, status, reviewer, created_at) "
                    + "VALUES (12, '2026Q1', 'OPEN', '李四', now() - interval '30 days')");
        }
        notifyRuleEngine.runOnce(LocalDate.now());
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement();
             ResultSet rs = s.executeQuery("SELECT count(*), min(message) FROM reminder_dispatch_log "
                     + "WHERE event_type = 'RULE_UAR_OVERDUE' AND org_id = 12")) {
            rs.next();
            assertEquals(1, rs.getInt(1), "超期 UAR 应产一条提醒");
            String msg = rs.getString(2);
            assertTrue(msg.contains("2026Q1"), "应渲染 {周期}：" + msg);
            assertTrue(msg.contains("李四"), "应渲染 {审阅人}：" + msg);
        }
    }

    @Test
    void d4_周期合规简报_汇总需重评制度数() throws Exception {
        Regulation reg = asOrg(ORG_PAY, () -> regulationService.create(ORG_PAY, "REG-D4", "反洗钱法",
                "全国人大", "反洗钱", LocalDate.of(2026, 1, 1), "摘要", "c"));
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement()) {
            s.executeUpdate("INSERT INTO regulation_policy_map(org_id, regulation_id, policy_id, clause, assess_stale) "
                    + "VALUES (12, " + reg.getId() + ", 1, '§1', TRUE)");
        }
        notifyRuleEngine.runOnce(LocalDate.now());
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement();
             ResultSet rs = s.executeQuery("SELECT count(*), min(message) FROM reminder_dispatch_log "
                     + "WHERE event_type = 'RULE_COMPLIANCE_DIGEST' AND org_id = 12")) {
            rs.next();
            assertEquals(1, rs.getInt(1), "org12 应产一条周报（同周去重）");
            assertTrue(rs.getString(2).contains("需重评制度 1 项"), "应渲染需重评数：" + rs.getString(2));
        }
    }

    // ---------- D1-8 §九 接线二：内核消费场景 + 升级链运行器 ----------

    @Test
    void s9接线_引擎新告警装配场景通知_跨子公司不外溢() throws Exception {
        long defId = remediationSceneDefId();
        // org12 装配「整改闭环」场景（接收 COMPLIANCE，升级链 L1/L2）；org13 也装配（接收 AUDIT）
        seedScene(ORG_PAY, defId, "[\"COMPLIANCE\"]", new int[][]{{1, 12}, {2, 48}}, new String[]{"MANAGER", "CISO"});
        seedScene(ORG_CF, defId, "[\"AUDIT\"]", new int[][]{}, new String[]{});
        long orderId = seedOverdueRemediation(ORG_PAY, 5);

        int produced = notifyRuleEngine.runOnce(LocalDate.now());
        assertTrue(produced >= 1, "应产出整改逾期告警");

        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement()) {
            // org12 命中场景 → 1 条 PENDING 场景通知，携接收角色快照与单据引用
            try (ResultSet rs = s.executeQuery("SELECT count(*), min(recipient_roles), min(status), min(object_type), min(object_id) "
                    + "FROM scene_notification WHERE org_id = 12")) {
                rs.next();
                assertEquals(1, rs.getInt(1), "org12 应生成 1 条场景通知");
                assertTrue(rs.getString(2).contains("COMPLIANCE"), "应承接场景接收角色快照");
                assertEquals("PENDING", rs.getString(3));
                assertEquals("REMEDIATION", rs.getString(4));
                assertEquals(orderId, rs.getLong(5), "应引用逾期整改单");
            }
            // 跨子公司不外溢：org12 的告警绝不落到 org13 的场景（org13 无对应告警）
            try (ResultSet rs = s.executeQuery("SELECT count(*) FROM scene_notification WHERE org_id = 13")) {
                rs.next();
                assertEquals(0, rs.getInt(1), "org12 告警不得为 org13 场景生成通知");
            }
        }

        // 幂等：再评估一轮，不重复生成
        notifyRuleEngine.runOnce(LocalDate.now());
        assertEquals(1, sceneNotifCount(ORG_PAY), "重复评估场景通知幂等");
    }

    @Test
    void s9接线_升级链按延迟逐级触发_幂等() throws Exception {
        long defId = remediationSceneDefId();
        seedScene(ORG_PAY, defId, "[\"COMPLIANCE\"]", new int[][]{{1, 12}, {2, 48}}, new String[]{"MANAGER", "CISO"});
        seedOverdueRemediation(ORG_PAY, 5);
        notifyRuleEngine.runOnce(LocalDate.now());
        assertEquals(1, sceneNotifCount(ORG_PAY), "先有一条待处理场景通知");

        java.time.OffsetDateTime base = java.time.OffsetDateTime.now();
        // 刚发生：无级到期
        assertEquals(0, escalationRunner.runOnce(base), "首发即刻无升级");
        // 过 13 小时：L1（12h）到期 → 升级到 MANAGER
        assertEquals(1, escalationRunner.runOnce(base.plusHours(13)), "L1 到期触发 1 级");
        assertEscalation(ORG_PAY, 1, "MANAGER");
        // 过 50 小时：L2（48h）到期 → 升级到 CISO
        assertEquals(1, escalationRunner.runOnce(base.plusHours(50)), "L2 到期触发 1 级");
        assertEscalation(ORG_PAY, 2, "CISO");
        assertEquals(2, escalationLogCount(ORG_PAY), "共触发 2 级");

        // 幂等：同一时刻再跑不重复升级
        assertEquals(0, escalationRunner.runOnce(base.plusHours(50)), "已触发级别不重复升级");
        assertEquals(2, escalationLogCount(ORG_PAY));
    }

    @Test
    void s9接线_已确认通知不再升级() throws Exception {
        long defId = remediationSceneDefId();
        seedScene(ORG_PAY, defId, "[\"COMPLIANCE\"]", new int[][]{{1, 12}}, new String[]{"MANAGER"});
        long orderId = seedOverdueRemediation(ORG_PAY, 5);
        notifyRuleEngine.runOnce(LocalDate.now());

        // 处理即止链：确认该单据的场景通知
        assertEquals(1, sceneNotifyConsumer.acknowledge(ORG_PAY, "REMEDIATION", orderId), "应确认 1 条");
        // 即便升级延迟早已越过，已确认者不再升级
        assertEquals(0, escalationRunner.runOnce(java.time.OffsetDateTime.now().plusHours(100)),
                "已确认通知不得升级");
        assertEquals(0, escalationLogCount(ORG_PAY));
    }

    // ---------- §九接线二 测试辅助（owner 直插，绕业务全链路聚焦内核行为） ----------

    private long remediationSceneDefId() throws Exception {
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement();
             ResultSet rs = s.executeQuery("SELECT id FROM notif_scene_def WHERE code = 'REMEDIATION_CLOSURE'")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    /** 装配一个运行态场景 + 升级链（delays[i]={level,delayHours}, roles[i]=升级角色）。 */
    private long seedScene(long orgId, long defId, String rolesJson, int[][] esc, String[] roles) throws Exception {
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement()) {
            long sceneId;
            try (ResultSet rs = s.executeQuery(
                    "INSERT INTO notification_scene(org_id, scene_def_id, name, recipient_roles, template, channel_type, org_scope) "
                            + "VALUES (" + orgId + ", " + defId + ", '整改闭环', '" + rolesJson + "', '整改逾期 {标题}', 'INBOX', 'SELF') RETURNING id")) {
                rs.next();
                sceneId = rs.getLong(1);
            }
            for (int i = 0; i < esc.length; i++) {
                s.executeUpdate("INSERT INTO notification_escalation(org_id, scene_id, level, delay_hours, escalate_to_role) "
                        + "VALUES (" + orgId + ", " + sceneId + ", " + esc[i][0] + ", " + esc[i][1] + ", '" + roles[i] + "')");
            }
            return sceneId;
        }
    }

    /** 造一条逾期 days 天的整改单，返回其 id。 */
    private long seedOverdueRemediation(long orgId, int days) throws Exception {
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement()) {
            long planId;
            try (ResultSet rs = s.executeQuery("INSERT INTO audit_plan(org_id, title, plan_start_date, external_status) "
                    + "VALUES (" + orgId + ", '接线二计划', '2099-01-01', 'DONE') RETURNING id")) {
                rs.next();
                planId = rs.getLong(1);
            }
            long findingId;
            try (ResultSet rs = s.executeQuery("INSERT INTO audit_finding(org_id, audit_plan_id, title, severity) "
                    + "VALUES (" + orgId + ", " + planId + ", '接线二发现', 'HIGH') RETURNING id")) {
                rs.next();
                findingId = rs.getLong(1);
            }
            try (ResultSet rs = s.executeQuery("INSERT INTO remediation_order(org_id, finding_id, assignee, due_date, measure) "
                    + "VALUES (" + orgId + ", " + findingId + ", '李四', current_date - " + days + ", '接线二整改') RETURNING id")) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private int sceneNotifCount(long orgId) throws Exception {
        return scalar("SELECT count(*) FROM scene_notification WHERE org_id = " + orgId);
    }

    private int escalationLogCount(long orgId) throws Exception {
        return scalar("SELECT count(*) FROM scene_escalation_log WHERE org_id = " + orgId);
    }

    private void assertEscalation(long orgId, int level, String role) throws Exception {
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement();
             ResultSet rs = s.executeQuery("SELECT count(*) FROM scene_escalation_log "
                     + "WHERE org_id = " + orgId + " AND level = " + level + " AND escalate_to_role = '" + role + "'")) {
            rs.next();
            assertEquals(1, rs.getInt(1), "应有 L" + level + "→" + role + " 的升级留痕");
        }
    }

    private int scalar(String sql) throws Exception {
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement();
             ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    @Test
    void 通知规则_临近开始_评估与审计计划() throws Exception {
        // 造：DRAFT 评估 start_date=今+3；PLANNED 内审计划 plan_start_date=今+3；两条 RULE 配置（窗口 7 天）
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement()) {
            s.executeUpdate("DELETE FROM notify_config WHERE org_id = 12 AND name LIKE '临近%'");
            s.executeUpdate("INSERT INTO assessment(org_id, title, status, start_date) "
                    + "VALUES (12, '年度等保自评', 'DRAFT', current_date + 3)");
            s.executeUpdate("INSERT INTO audit_plan(org_id, title, plan_start_date, status, audit_type) "
                    + "VALUES (12, '支付内审', current_date + 3, 'PLANNED', 'INTERNAL')");
            s.executeUpdate("INSERT INTO notify_config(org_id, kind, name, detail, enabled) VALUES "
                    + "(12, 'RULE', '临近评估', '{\"source\":\"ASSESSMENT_UPCOMING\",\"days\":7,"
                    + "\"template\":\"评估「{标题}」将于 {开始日} 开始（剩 {剩余天数} 天）\"}', true)");
            s.executeUpdate("INSERT INTO notify_config(org_id, kind, name, detail, enabled) VALUES "
                    + "(12, 'RULE', '临近审计', '{\"source\":\"AUDIT_PLAN_UPCOMING\",\"days\":7,"
                    + "\"template\":\"{类型}计划「{标题}」将于 {开始日} 开始（剩 {剩余天数} 天）\"}', true)");
        }

        notifyRuleEngine.runOnce(LocalDate.now());

        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement();
             ResultSet rs = s.executeQuery("SELECT event_type, message FROM reminder_dispatch_log "
                     + "WHERE event_type IN ('RULE_ASSESSMENT_UPCOMING','RULE_AUDIT_UPCOMING') AND org_id = 12 ORDER BY event_type")) {
            java.util.Map<String, String> got = new java.util.HashMap<>();
            while (rs.next()) {
                got.put(rs.getString(1), rs.getString(2));
            }
            assertTrue(got.containsKey("RULE_ASSESSMENT_UPCOMING"), "应产出评估临近开始提醒");
            assertTrue(got.get("RULE_ASSESSMENT_UPCOMING").contains("年度等保自评"), "评估提醒渲染标题");
            assertTrue(got.get("RULE_ASSESSMENT_UPCOMING").contains("剩 3 天"), "评估提醒渲染剩余天数");
            assertTrue(got.containsKey("RULE_AUDIT_UPCOMING"), "应产出审计计划临近开始提醒");
            assertTrue(got.get("RULE_AUDIT_UPCOMING").contains("内部审计"), "审计提醒渲染类型");
        }
        // 幂等
        notifyRuleEngine.runOnce(LocalDate.now());
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement();
             ResultSet rs = s.executeQuery("SELECT count(*) FROM reminder_dispatch_log "
                     + "WHERE event_type IN ('RULE_ASSESSMENT_UPCOMING','RULE_AUDIT_UPCOMING') AND org_id = 12")) {
            rs.next();
            assertEquals(2, rs.getInt(1), "同计划同开始日重复评估不重复提醒");
        }
    }

    @Test
    void b_制度本地导入_解析标题编号版本生效日期() throws Exception {
        byte[] docx = buildPolicyDocx(List.of(
                "MD-2026-001 数据安全管理办法",
                "版本：V2",
                "生效日期：2026-07-01",
                "第一条 为规范数据处理活动，保障数据安全，制定本办法。"));
        var parsed = asOrg(ORG_PAY, () -> policyService.parseImport("数据安全管理办法.docx", docx));
        assertEquals("MD-2026-001", parsed.code(), "应识别前导编号");
        assertEquals("数据安全管理办法", parsed.title(), "应从标题行拆出名称");
        assertEquals(2, parsed.version(), "应识别版本 V2");
        assertEquals(LocalDate.of(2026, 7, 1), parsed.effectiveDate(), "应识别生效日期");
        assertTrue(parsed.content().contains("为规范数据处理活动"), "应保留全文");

        // 用解析结果建档：版本/生效日期落库
        var policy = asOrg(ORG_PAY, () -> policyService.create(ORG_PAY, parsed.code(), parsed.title(),
                parsed.content(), parsed.version(), parsed.effectiveDate(), "c"));
        assertEquals(2, policy.getVersion());
        assertEquals(LocalDate.of(2026, 7, 1), policy.getEffectiveDate());
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

    /** 构造一份多段落 docx（每个元素一段），用于制度导入解析测试。 */
    private static byte[] buildPolicyDocx(List<String> paragraphs) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (String p : paragraphs) {
                doc.createParagraph().createRun().setText(p);
            }
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
