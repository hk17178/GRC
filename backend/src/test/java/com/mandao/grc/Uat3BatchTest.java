package com.mandao.grc;

import com.mandao.grc.common.isolation.IsolationContext;
import com.mandao.grc.modules.assessment.Assessment;
import com.mandao.grc.modules.assessment.AssessmentService;
import com.mandao.grc.modules.audit.management.AuditPlan;
import com.mandao.grc.modules.audit.management.AuditPlanService;
import com.mandao.grc.modules.audit.management.AuditReport;
import com.mandao.grc.modules.audit.management.AuditReportService;
import com.mandao.grc.modules.audit.management.AuditReportTemplate;
import com.mandao.grc.modules.audit.management.AuditType;
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
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UAT 三轮批次集成测试（V53-V55）。验证：
 *  1) 签批存证：手写签名字节落库、sha256 指纹固化（哈希链留痕由 service 统一追加）；
 *  2) 报告模板：选模板生成草稿=模板骨架+系统组稿附录；停用模板被拒；模板 CRUD；
 *  3) 人行追踪源种子：两个 HTTP 源（国家法律/部门规章）已内置且启用。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "grc.scheduler.enabled=false")
@Testcontainers
class Uat3BatchTest {

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
    private AssessmentService assessmentService;
    @Autowired
    private AuditPlanService planService;
    @Autowired
    private AuditReportService reportService;

    private static final long ORG_PAY = 12L;

    @BeforeEach
    void clean() throws Exception {
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement()) {
            s.executeUpdate("TRUNCATE audit_report, remediation_order, audit_finding, audit_plan CASCADE");
            s.executeUpdate("TRUNCATE risk_finding, assessment CASCADE");
            s.executeUpdate("DELETE FROM audit_report_template WHERE org_id <> 1");
        }
    }

    @AfterEach
    void clearContext() {
        IsolationContext.clear();
    }

    @Test
    void 签批存证_签名落库_指纹固化() {
        Assessment a = asOrg(ORG_PAY, () -> assessmentService.create(ORG_PAY, "签批评估", "u", "2026", "c"));
        byte[] fakePng = "PNG-签名字节".getBytes(StandardCharsets.UTF_8);

        Assessment signed = asOrg(ORG_PAY, () ->
                assessmentService.signOff(a.getId(), "risk_mgr", "同意接受", true, fakePng));
        assertEquals("risk_mgr", signed.getMgmtSigner());
        assertNotNull(signed.getMgmtSignatureSha256(), "签名指纹应固化");
        assertEquals(64, signed.getMgmtSignatureSha256().length());
        assertTrue(java.util.Arrays.equals(fakePng,
                asOrg(ORG_PAY, () -> assessmentService.get(a.getId())).getMgmtSignature()), "签名原图应落库");
    }

    @Test
    void 报告模板_骨架加附录_停用被拒_CRUD() {
        // 内置模板可见（org1 种子，含个人信息保护合规审计模板）
        List<AuditReportTemplate> builtins = asOrg(1L, () -> reportService.listTemplates());
        assertTrue(builtins.stream().anyMatch(t -> t.getName().contains("个人信息保护合规审计")),
                "应内置个保合规审计报告模板");
        AuditReportTemplate piTpl = builtins.stream()
                .filter(t -> t.getName().contains("个人信息保护")).findFirst().orElseThrow();

        AuditPlan plan = asOrg(ORG_PAY, () ->
                planService.create(ORG_PAY, "个人信息保护合规审计", AuditType.INTERNAL, LocalDate.now(), "c"));
        AuditReport draft = asOrgs(List.of(1L, 12L), () ->
                reportService.createDraft(plan.getId(), piTpl.getId(), "auditor"));
        assertTrue(draft.getContent().startsWith("一、审计基本情况"), "草稿应以模板骨架开头");
        assertTrue(draft.getContent().contains("系统组稿附录"), "系统组稿应作为附录随后");
        assertTrue(draft.getContent().contains("跨境提供合规路径"), "个保模板要点应在正文");

        // 自建模板 CRUD + 停用被拒
        AuditReportTemplate mine = asOrg(ORG_PAY, () ->
                reportService.createTemplate(ORG_PAY, "等保审计模板", "等保", "一、测评概况\n", "c"));
        asOrg(ORG_PAY, () -> reportService.updateTemplate(mine.getId(), null, "等保2.0", "一、测评概况（改）\n", "c"));
        asOrg(ORG_PAY, () -> reportService.setTemplateEnabled(mine.getId(), false));
        AuditPlan plan2 = asOrg(ORG_PAY, () ->
                planService.create(ORG_PAY, "等保审计", AuditType.INTERNAL, LocalDate.now(), "c"));
        assertThrows(IllegalStateException.class, () -> asOrg(ORG_PAY, () ->
                reportService.createDraft(plan2.getId(), mine.getId(), "c")), "停用模板生成应被拒");
        asOrg(ORG_PAY, () -> { reportService.deleteTemplate(mine.getId(), "c"); return null; });
    }

    @Test
    void 人行追踪源种子_两源内置且启用() throws Exception {
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement()) {
            ResultSet rs = s.executeQuery("SELECT count(*) FROM regulation_source "
                    + "WHERE name LIKE '中国人民银行%' AND source_type = 'HTTP' AND enabled = true");
            rs.next();
            assertEquals(2, rs.getInt(1), "应内置人行 国家法律+部门规章 两个启用源");
            ResultSet cfg = s.executeQuery("SELECT config FROM regulation_source WHERE name LIKE '中国人民银行%' LIMIT 1");
            cfg.next();
            assertTrue(cfg.getString(1).contains("newslist_style"), "选择器应按 pbc.gov.cn 实际结构配置");
        }
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
