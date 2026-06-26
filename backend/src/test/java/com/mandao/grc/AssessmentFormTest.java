package com.mandao.grc;

import com.mandao.grc.common.isolation.IsolationContext;
import com.mandao.grc.modules.assessment.Assessment;
import com.mandao.grc.modules.assessment.AssessmentService;
import com.mandao.grc.modules.assessment.TemplateService;
import com.mandao.grc.modules.assessment.RiskCloseGateException;
import com.mandao.grc.modules.assessment.RiskLevel;
import com.mandao.grc.modules.assessment.form.AssessmentFormService;
import com.mandao.grc.modules.assessment.form.DocxFormParser;
import com.mandao.grc.modules.assessment.form.FormSchema;
import com.mandao.grc.modules.assessment.form.ScoringService;
import com.mandao.grc.modules.assessment.form.TemplateForm;
import com.mandao.grc.modules.control.ControlFramework;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
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
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 风险评估表单引擎 P1 集成测试（真实 PG + 切面 + RLS）。验证：
 *  1) .docx 占位符解析：章节(标题)/标量字段(类型/选项)/明细表(列) 正确成 schema；
 *  2) 模板侧：上传→DRAFT、启用→ACTIVE(同模板唯一)；
 *  3) 评估侧：关联模板的评估首访自动绑定 ACTIVE 表单快照、保存填写并可回读；
 *  4) 组织隔离：org12 的表单 org13 不可见。
 *
 * 设计依据：D1-6（表单引擎）、D2-5。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "grc.scheduler.enabled=false")
@Testcontainers
class AssessmentFormTest {

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

    @Autowired private DocxFormParser parser;
    @Autowired private AssessmentFormService formService;
    @Autowired private TemplateService templateService;
    @Autowired private AssessmentService assessmentService;
    @Autowired private ScoringService scoringService;

    private static final long ORG_PAY = 12L;
    private static final long ORG_CF = 13L;

    @BeforeEach
    void clean() throws Exception {
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement()) {
            s.executeUpdate("TRUNCATE assessment_answer, template_form, assessment_template_item, "
                    + "assessment_template, operation_log RESTART IDENTITY CASCADE");
            s.executeUpdate("DELETE FROM assessment WHERE id >= 1000");
        }
    }

    @AfterEach
    void clearContext() {
        IsolationContext.clear();
    }

    @Test
    void 解析docx_章节字段明细表() {
        FormSchema schema = parser.parse(sampleDocx());
        assertEquals(1, schema.sections().size(), "应识别 1 个标题章节");
        FormSchema.Section sec = schema.sections().get(0);
        assertEquals("一、评估概况", sec.title());

        // 标量字段
        assertEquals(3, sec.fields().size(), "应解析 3 个标量字段");
        FormSchema.Field period = sec.fields().stream().filter(f -> f.key().equals("评估周期")).findFirst().orElseThrow();
        assertEquals("date", period.type());
        FormSchema.Field method = sec.fields().stream().filter(f -> f.key().equals("评估方法")).findFirst().orElseThrow();
        assertEquals("select", method.type());
        assertEquals(List.of("文档审查", "访谈"), method.options());

        // 明细表
        assertEquals(1, sec.lists().size(), "应解析 1 个明细表");
        FormSchema.ListBlock list = sec.lists().get(0);
        assertEquals("风险点清单", list.key());
        assertEquals(3, list.columns().size(), "明细表应 3 列");
        FormSchema.Field residual = list.columns().stream().filter(c -> c.key().equals("残余风险")).findFirst().orElseThrow();
        assertEquals("level", residual.type());
        assertEquals("残余风险", residual.label(), "列标签应取表头");
    }

    @Test
    void 无占位符文档_拒绝() {
        assertThrows(IllegalArgumentException.class, () -> {
            XWPFDocument doc = new XWPFDocument();
            doc.createParagraph().createRun().setText("一份没有占位符的普通文档");
            assertNotNull(parser.parse(toBytes(doc)));
        });
    }

    @Test
    void 模板上传启用_与评估填写回读() {
        Long tplId = asOrg(ORG_PAY, () -> templateService.create(ORG_PAY, "TPL-RA", "信息安全风险评估",
                ControlFramework.ISO27001, "标准风险评估", "owner", "c").getId());

        // 上传 → DRAFT
        TemplateForm form = asOrg(ORG_PAY, () -> formService.uploadForm(tplId, null, sampleDocx()));
        assertEquals(TemplateForm.DRAFT, form.getStatus());
        assertEquals(1, form.getVersionNo());

        // 启用 → ACTIVE
        TemplateForm active = asOrg(ORG_PAY, () -> formService.activate(form.getId()));
        assertEquals(TemplateForm.ACTIVE, active.getStatus());

        // 关联模板的评估，首访自动绑定表单
        Assessment a = asOrg(ORG_PAY, () -> assessmentService.create(ORG_PAY, "2026Q2 风评", "张三", "2026Q2", tplId, "c"));
        AssessmentFormService.AssessmentFormView view = asOrg(ORG_PAY, () -> formService.getAssessmentForm(a.getId()));
        assertTrue(view.hasForm(), "关联模板且模板已启用表单 → 应可填写");
        assertEquals(active.getId(), view.formVersionId(), "应绑定 ACTIVE 表单快照");
        assertNotNull(view.schema());

        // 保存填写 → 回读
        Map<String, Object> answers = Map.of(
                "评估对象", "核心支付系统",
                "评估方法", "文档审查",
                "风险点清单", List.of(Map.of("控制点", "ACL-01", "残余风险", "LOW")));
        asOrg(ORG_PAY, () -> { formService.saveAnswers(a.getId(), answers); return null; });

        AssessmentFormService.AssessmentFormView reloaded = asOrg(ORG_PAY, () -> formService.getAssessmentForm(a.getId()));
        @SuppressWarnings("unchecked")
        Map<String, Object> back = (Map<String, Object>) reloaded.answers();
        assertEquals("核心支付系统", back.get("评估对象"));
        assertNotNull(back.get("风险点清单"), "明细表行应回读");
    }

    @Test
    void 未关联模板的评估_无表单() {
        Assessment a = asOrg(ORG_PAY, () -> assessmentService.create(ORG_PAY, "无模板评估", "李四", "2026Q2", "c"));
        AssessmentFormService.AssessmentFormView view = asOrg(ORG_PAY, () -> formService.getAssessmentForm(a.getId()));
        assertFalse(view.hasForm(), "未关联模板 → 无表单");
    }

    @Test
    void 组织隔离_org12表单org13不可见() {
        Long tplId = asOrg(ORG_PAY, () -> templateService.create(ORG_PAY, "TPL-ISO", "x",
                ControlFramework.ISO27001, null, null, "c").getId());
        asOrg(ORG_PAY, () -> formService.uploadForm(tplId, null, sampleDocx()));
        assertEquals(1, asOrg(ORG_PAY, () -> formService.listForms(tplId)).size(), "org12 应看到自己的表单");
        assertTrue(asOrg(ORG_CF, () -> formService.listForms(tplId)).isEmpty(), "org13 不应看到 org12 的表单");
    }

    @Test
    void 打分_可能性乘影响映射五级() {
        assertEquals(RiskLevel.VERY_LOW, scoringService.levelOf(1, 1));   // 1
        assertEquals(RiskLevel.LOW, scoringService.levelOf(2, 3));        // 6
        assertEquals(RiskLevel.MID, scoringService.levelOf(3, 3));        // 9
        assertEquals(RiskLevel.HIGH, scoringService.levelOf(4, 5));       // 20
        assertEquals(RiskLevel.VERY_HIGH, scoringService.levelOf(5, 5));  // 25
    }

    @Test
    void 回填报告docx_标量与明细表多行() throws Exception {
        Long tplId = asOrg(ORG_PAY, () -> templateService.create(ORG_PAY, "TPL-RPT", "风评报告",
                ControlFramework.ISO27001, null, null, "c").getId());
        TemplateForm form = asOrg(ORG_PAY, () -> formService.uploadForm(tplId, null, sampleDocx()));
        asOrg(ORG_PAY, () -> formService.activate(form.getId()));
        Assessment a = asOrg(ORG_PAY, () -> assessmentService.create(ORG_PAY, "导出评估", "张三", "2026Q2", tplId, "c"));
        asOrg(ORG_PAY, () -> formService.getAssessmentForm(a.getId()));

        Map<String, Object> answers = Map.of(
                "评估对象", "核心支付清算系统",
                "整体风险等级", "HIGH",
                "风险点清单", List.of(
                        Map.of("控制点", "ACL-01", "结论", "访问控制不足", "残余风险", "HIGH"),
                        Map.of("控制点", "ACL-02", "结论", "日志留存不足", "残余风险", "MID")));
        asOrg(ORG_PAY, () -> { formService.saveAnswers(a.getId(), answers); return null; });

        byte[] docx = asOrg(ORG_PAY, () -> formService.buildReportDocx(a.getId()));
        String txt;
        try (org.apache.poi.xwpf.usermodel.XWPFDocument d =
                     new org.apache.poi.xwpf.usermodel.XWPFDocument(new java.io.ByteArrayInputStream(docx));
             org.apache.poi.xwpf.extractor.XWPFWordExtractor ex =
                     new org.apache.poi.xwpf.extractor.XWPFWordExtractor(d)) {
            txt = ex.getText();
        }
        assertTrue(txt.contains("核心支付清算系统"), "标量字段应回填");
        assertTrue(txt.contains("ACL-01") && txt.contains("ACL-02"), "明细表两行均应回填");
        assertTrue(txt.contains("访问控制不足"), "明细表文本列应回填");
        assertTrue(txt.contains("高") && txt.contains("中"), "level 应转中文档位（HIGH→高 / MID→中）");
        assertFalse(txt.contains("${"), "不应残留占位符");
    }

    @Test
    void 残余聚合_完成门控_管理层签批放行() {
        Long tplId = asOrg(ORG_PAY, () -> templateService.create(ORG_PAY, "TPL-GATE", "风评门控",
                ControlFramework.ISO27001, null, null, "c").getId());
        TemplateForm form = asOrg(ORG_PAY, () -> formService.uploadForm(tplId, null, sampleDocx()));
        asOrg(ORG_PAY, () -> formService.activate(form.getId()));

        Assessment a = asOrg(ORG_PAY, () -> assessmentService.create(ORG_PAY, "门控评估", "张三", "2026Q2", tplId, "c"));
        // 绑定表单（首访）+ 填写：风险点清单含一行 残余风险=HIGH
        asOrg(ORG_PAY, () -> formService.getAssessmentForm(a.getId()));
        Map<String, Object> answers = Map.of(
                "风险点清单", List.of(Map.of("控制点", "ACL-01", "残余风险", "HIGH")));
        RiskLevel overall = asOrg(ORG_PAY, () -> formService.saveAnswers(a.getId(), answers));
        assertEquals(RiskLevel.HIGH, overall, "聚合整体残余应为 HIGH");
        assertEquals(RiskLevel.HIGH, asOrg(ORG_PAY, () -> assessmentService.get(a.getId()).getRiskLevel()),
                "整体残余应写回评估");

        // 推进到待复核
        asOrg(ORG_PAY, () -> assessmentService.start(a.getId(), "c"));
        asOrg(ORG_PAY, () -> assessmentService.submitForReview(a.getId(), "c"));

        // 残余高 + 未签批 → 完成被门控（CR-002 红线）
        assertThrows(RiskCloseGateException.class,
                () -> runAsOrg(ORG_PAY, () -> assessmentService.complete(a.getId(), "c")));

        // 管理层接受签批 → 放行完成
        asOrg(ORG_PAY, () -> assessmentService.signOff(a.getId(), "ceo", "知悉并接受", true));
        assertEquals(com.mandao.grc.modules.assessment.AssessmentStatus.COMPLETED,
                asOrg(ORG_PAY, () -> assessmentService.complete(a.getId(), "c").getStatus()),
                "签批后应可完成");
    }

    // ---------- 辅助 ----------

    /** 造一份含 标题/标量占位符/明细表 的 .docx 字节。 */
    private byte[] sampleDocx() {
        XWPFDocument doc = new XWPFDocument();
        XWPFParagraph h = doc.createParagraph();
        h.setStyle("Heading1");
        h.createRun().setText("一、评估概况");
        doc.createParagraph().createRun().setText("评估对象：${评估对象}");
        doc.createParagraph().createRun().setText("评估周期：${评估周期|date}  方法：${评估方法|select:文档审查;访谈}");
        // 明细表标记 + 表格
        doc.createParagraph().createRun().setText("${#风险点清单}");
        XWPFTable table = doc.createTable(2, 3);
        table.getRow(0).getCell(0).setText("控制点");
        table.getRow(0).getCell(1).setText("结论");
        table.getRow(0).getCell(2).setText("残余风险");
        table.getRow(1).getCell(0).setText("${控制点}");
        table.getRow(1).getCell(1).setText("${结论|textarea}");
        table.getRow(1).getCell(2).setText("${残余风险|level}");
        return toBytes(doc);
    }

    private byte[] toBytes(XWPFDocument doc) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            doc.write(bos);
            doc.close();
            return bos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
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

    private void runAsOrg(long orgId, java.util.concurrent.Callable<?> action) throws Exception {
        IsolationContext.set(List.of(orgId));
        try {
            action.call();
        } finally {
            IsolationContext.clear();
        }
    }
}
