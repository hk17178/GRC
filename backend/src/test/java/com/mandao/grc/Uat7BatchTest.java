package com.mandao.grc;

import com.mandao.grc.common.isolation.IsolationContext;
import com.mandao.grc.kernel.ExpiryScanService;
import com.mandao.grc.modules.ai.AiEgressGuard;
import com.mandao.grc.modules.assessment.Assessment;
import com.mandao.grc.modules.assessment.AssessmentService;
import com.mandao.grc.modules.assessment.TemplateService;
import com.mandao.grc.modules.assessment.form.AssessmentFormService;
import com.mandao.grc.modules.asset.Asset;
import com.mandao.grc.modules.asset.AssetClassification;
import com.mandao.grc.modules.asset.AssetService;
import com.mandao.grc.modules.atv.AtvService;
import com.mandao.grc.modules.atv.Threat;
import com.mandao.grc.modules.atv.Vulnerability;
import com.mandao.grc.modules.audit.management.EvidenceService;
import com.mandao.grc.modules.audit.management.RemediationOrder;
import com.mandao.grc.modules.audit.management.RemediationService;
import com.mandao.grc.modules.audit.management.RemediationStatus;
import com.mandao.grc.modules.regulatory.MajorIncidentReport;
import com.mandao.grc.modules.regulatory.MajorIncidentService;
import com.mandao.grc.modules.regulatory.MajorIncidentSeverity;
import com.mandao.grc.modules.regulatory.MajorIncidentStatus;
import com.mandao.grc.modules.regulatory.RegFiling;
import com.mandao.grc.modules.regulatory.RegFilingService;
import com.mandao.grc.modules.regulatory.RegFilingStatus;
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

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UAT 七轮批次集成测试（评估报告排期 7-1~7-13 的可测核心）。验证：
 *  1) 7-1 整改闭环红线：无证据说明/证据库无挂件不可提交；责任人不得自行验证；
 *  2) 7-2 报送回执红线：无回执证据不可了结；重大事件 上报→确认→挂回执→了结 全链与法定时限预警；
 *  3) 7-12 签批 SoD：评估人不得自行完成管理层签批；
 *  4) 7-13 重新预填：先绑空表单→纳入范围资产→reprefill 后三张明细表有值；
 *  5) 7-10 AI 出站守卫：不出域开关/白名单拦截。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "grc.scheduler.enabled=false")
@Testcontainers
class Uat7BatchTest {

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
    private RemediationService remediationService;
    @Autowired
    private EvidenceService evidenceService;
    @Autowired
    private RegFilingService regFilingService;
    @Autowired
    private MajorIncidentService majorIncidentService;
    @Autowired
    private ExpiryScanService expiryScanService;
    @Autowired
    private AssessmentService assessmentService;
    @Autowired
    private AssessmentFormService formService;
    @Autowired
    private TemplateService templateService;
    @Autowired
    private AssetService assetService;
    @Autowired
    private AtvService atvService;

    private static final long ORG_PAY = 12L;

    @BeforeEach
    void clean() throws Exception {
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement()) {
            s.executeUpdate("TRUNCATE evidence, remediation_order CASCADE");
            s.executeUpdate("TRUNCATE risk_treatment, risk_finding, assessment_answer, assessment_asset, "
                    + "assessment_doc, risk_scenario, threat, vulnerability, asset CASCADE");
            s.executeUpdate("DELETE FROM assessment WHERE org_id = 12");
            s.executeUpdate("DELETE FROM major_incident_report");
            s.executeUpdate("DELETE FROM reminder_dispatch_log WHERE event_type = 'MAJOR_INCIDENT_REPORT_DUE'");
        }
    }

    @AfterEach
    void clearContext() {
        IsolationContext.clear();
    }

    /** 造审计计划+发现（整改单外键前置），返回 findingId。 */
    private long seedAuditFinding() throws Exception {
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement()) {
            long planId;
            try (ResultSet rs = s.executeQuery(
                    "INSERT INTO audit_plan(org_id, title, plan_start_date, external_status) "
                            + "VALUES (12, '七轮整改门控计划', '2099-01-01', 'DONE') RETURNING id")) {
                rs.next();
                planId = rs.getLong(1);
            }
            try (ResultSet rs = s.executeQuery(
                    "INSERT INTO audit_finding(org_id, audit_plan_id, title, severity) "
                            + "VALUES (12, " + planId + ", '七轮门控发现', 'HIGH') RETURNING id")) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    @Test
    void 整改红线_无证据不可提交_责任人不得自验() throws Exception {
        long findingId = seedAuditFinding();
        RemediationOrder o = asOrg(ORG_PAY, () -> remediationService.create(findingId, "张三",
                LocalDate.now().plusDays(7), "补齐日志留存", "下达人"));
        asOrg(ORG_PAY, () -> remediationService.start(o.getId(), "张三"));

        // 证据说明为空 → 拒
        assertThrows(IllegalStateException.class, () -> asOrg(ORG_PAY, () ->
                remediationService.submit(o.getId(), " ", "张三")), "空证据说明必须被拒");
        // 有说明但证据库无挂件 → 拒
        assertThrows(IllegalStateException.class, () -> asOrg(ORG_PAY, () ->
                remediationService.submit(o.getId(), "已完成整改", "张三")), "证据库无挂件必须被拒");

        // 上传证据后可提交
        asOrg(ORG_PAY, () -> evidenceService.upload(ORG_PAY, null, null, o.getId(),
                "整改截图", "fix.png", "image/png", "png-bytes".getBytes(StandardCharsets.UTF_8), "张三"));
        RemediationOrder submitted = asOrg(ORG_PAY, () ->
                remediationService.submit(o.getId(), "已完成整改，见证据库截图", "张三"));
        assertEquals(RemediationStatus.SUBMITTED, submitted.getStatus());

        // 职责分离：责任人张三不得自验
        assertThrows(IllegalStateException.class, () -> asOrg(ORG_PAY, () ->
                remediationService.verify(o.getId(), "张三")), "责任人不得自行验证");
        RemediationOrder verified = asOrg(ORG_PAY, () -> remediationService.verify(o.getId(), "验证人李四"));
        assertEquals(RemediationStatus.VERIFIED, verified.getStatus());
    }

    @Test
    void 报送回执红线_重大事件全链_法定时限预警() throws Exception {
        // ---- 报送：SUBMITTED 无回执不可了结 ----
        RegFiling f = asOrg(ORG_PAY, () -> regFilingService.create(ORG_PAY, "反洗钱月报", "人行",
                LocalDate.now().plusDays(10), "c"));
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement()) {
            s.executeUpdate("UPDATE reg_filing SET status = 'SUBMITTED' WHERE id = " + f.getId());
        }
        assertThrows(IllegalStateException.class, () -> asOrg(ORG_PAY, () ->
                regFilingService.close(f.getId(), "c")), "无回执证据不可了结");
        asOrg(ORG_PAY, () -> evidenceService.upload(ORG_PAY, null, null, null, f.getId(), null,
                "人行受理回执", "receipt.pdf", "application/pdf",
                "pdf".getBytes(StandardCharsets.UTF_8), "c"));
        assertEquals(RegFilingStatus.CLOSED,
                asOrg(ORG_PAY, () -> regFilingService.close(f.getId(), "c")).getStatus());

        // ---- 重大事件：时限预警 + 上报→确认→挂回执→了结 ----
        LocalDate deadline = LocalDate.of(2026, 8, 10);
        MajorIncidentReport mi = asOrg(ORG_PAY, () -> majorIncidentService.create(ORG_PAY, "支付通道故障",
                MajorIncidentSeverity.VERY_HIGH, OffsetDateTime.now(), deadline, "c"));
        // 距时限 1 天：到期扫描应产 MAJOR_INCIDENT_REPORT_DUE
        expiryScanService.scanOnce(deadline.minusDays(1));
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement();
             ResultSet rs = s.executeQuery("SELECT count(*) FROM reminder_dispatch_log "
                     + "WHERE event_type = 'MAJOR_INCIDENT_REPORT_DUE' AND object_id = " + mi.getId())) {
            rs.next();
            assertEquals(1, rs.getInt(1), "法定时限临近应产预警");
        }

        asOrg(ORG_PAY, () -> majorIncidentService.report(mi.getId(), "c"));
        // 未经监管确认不可了结（状态门控）
        assertThrows(IllegalStateException.class, () -> asOrg(ORG_PAY, () ->
                majorIncidentService.close(mi.getId(), "c")));
        asOrg(ORG_PAY, () -> majorIncidentService.acknowledge(mi.getId(), "c"));
        // 已确认但无回执证据 → 拒
        assertThrows(IllegalStateException.class, () -> asOrg(ORG_PAY, () ->
                majorIncidentService.close(mi.getId(), "c")), "无回执证据不可了结");
        asOrg(ORG_PAY, () -> evidenceService.upload(ORG_PAY, null, null, null, null, mi.getId(),
                "事件报送回执", "ack.pdf", "application/pdf",
                "pdf".getBytes(StandardCharsets.UTF_8), "c"));
        assertEquals(MajorIncidentStatus.CLOSED,
                asOrg(ORG_PAY, () -> majorIncidentService.close(mi.getId(), "c")).getStatus());
    }

    @Test
    void 签批SoD_评估人不得自签() {
        Assessment a = asOrg(ORG_PAY, () -> assessmentService.create(ORG_PAY, "SoD 验证评估", "risk_officer_wang",
                "2026", "risk_officer_wang"));
        assertThrows(IllegalStateException.class, () -> asOrg(ORG_PAY, () ->
                        assessmentService.signOff(a.getId(), "risk_officer_wang", "自批自签", true, null)),
                "评估人不得自行管理层签批");
        // 独立签批人可签
        Assessment signed = asOrg(ORG_PAY, () ->
                assessmentService.signOff(a.getId(), "ceo_li", "同意接受", true, null));
        assertEquals("ceo_li", signed.getMgmtSigner());
    }

    @Test
    @SuppressWarnings("unchecked")
    void 重新预填_范围资产后置补录也能同步三表() {
        var tpl = asOrg(1L, () -> templateService.list().stream()
                .filter(t -> "TPL-MLPS".equals(t.getCode())).findFirst().orElseThrow());
        Assessment a = asOrgs(List.of(1L, 12L), () ->
                assessmentService.create(ORG_PAY, "预填时序评估", "u", "2026", tpl.getId(), "c"));
        // 真实操作顺序：先打开表单（绑死空快照）
        var before = asOrgs(List.of(1L, 12L), () -> formService.getAssessmentForm(a.getId()));
        List<Map<String, Object>> beforeAssets =
                (List<Map<String, Object>>) ((Map<String, Object>) before.answers()).get("资产清单");
        assertTrue(beforeAssets == null || beforeAssets.isEmpty(), "先开表单时清单应为空（复现 A28）");

        // 再补录资产与场景并纳入范围
        Asset asset = asOrg(ORG_PAY, () -> assetService.register(ORG_PAY, "清算核心", "SYSTEM", "o",
                AssetClassification.SENSITIVE, true, false, true, false, "CRITICAL", "c"));
        Threat t = asOrg(ORG_PAY, () -> atvService.createThreat(ORG_PAY, "T7", "内部误操作", "人员", null, "c"));
        Vulnerability v = asOrg(ORG_PAY, () -> atvService.createVulnerability(ORG_PAY, "V7", "变更未评审", "流程", null, "c"));
        asOrg(ORG_PAY, () -> atvService.createScenario(asset.getId(), t.getId(), v.getId(), 3, 4, "变更管理缺失", "c"));
        asOrg(ORG_PAY, () -> assessmentService.addScopeAsset(a.getId(), asset.getId(), "c"));

        // 重新预填 → 三张系统明细表有值
        var after = asOrgs(List.of(1L, 12L), () -> formService.reprefill(a.getId(), "c"));
        Map<String, Object> answers = (Map<String, Object>) after.answers();
        assertEquals(1, ((List<?>) answers.get("资产清单")).size(), "重新预填后资产清单应有范围资产");
        assertEquals("内部误操作",
                ((List<Map<String, Object>>) answers.get("威胁脆弱性清单")).get(0).get("威胁"));
        assertTrue(String.valueOf(((List<Map<String, Object>>) answers.get("风险清单")).get(0).get("风险描述"))
                .contains("清算核心"));
    }

    @Test
    void AI出站守卫_不出域开关与白名单() {
        // 开关关闭：一律拒绝
        AiEgressGuard off = new AiEgressGuard(false, "api.anthropic.com", 1000, 1000);
        IllegalStateException e1 = assertThrows(IllegalStateException.class,
                () -> off.clientFor("https://api.anthropic.com"));
        assertTrue(e1.getMessage().contains("不出域"), e1.getMessage());

        // 白名单外主机：拒绝
        AiEgressGuard on = new AiEgressGuard(true, "api.anthropic.com,api.openai.com", 1000, 1000);
        IllegalStateException e2 = assertThrows(IllegalStateException.class,
                () -> on.clientFor("https://evil.example.com/v1"));
        assertTrue(e2.getMessage().contains("白名单"), e2.getMessage());

        // 地址非法：拒绝
        assertThrows(IllegalStateException.class, () -> on.clientFor("not a url"));
    }

    private <T> T asOrg(long orgId, Supplier<T> action) {
        return asOrgs(List.of(orgId), action);
    }

    private <T> T asOrgs(List<Long> orgIds, Supplier<T> action) {
        IsolationContext.set(orgIds);
        try {
            return action.get();
        } finally {
            IsolationContext.clear();
        }
    }
}
