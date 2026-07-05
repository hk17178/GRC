package com.mandao.grc;

import com.mandao.grc.common.isolation.IsolationContext;
import com.mandao.grc.modules.assessment.Assessment;
import com.mandao.grc.modules.assessment.AssessmentItem;
import com.mandao.grc.modules.assessment.AssessmentItemResult;
import com.mandao.grc.modules.assessment.AssessmentItemService;
import com.mandao.grc.modules.assessment.TemplateService;
import com.mandao.grc.modules.assessment.TemplateStatus;
import com.mandao.grc.modules.audit.ChainVerifyResult;
import com.mandao.grc.modules.audit.HashChainService;
import com.mandao.grc.modules.control.ControlFramework;
import com.mandao.grc.modules.control.ControlService;
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
 * 评估模板库 + 实例化 + 评估项集成测试（真实 PG + 切面 + RLS）。验证：
 *  1) 模板生命周期 + 实例化：建模板→加项(引用控件)→发布→实例化为评估+评估项(复用控件)→逐项评估；
 *  2) 发布门控（空模板不可发布）、实例化门控（未发布不可实例化）；
 *  3) 组织隔离：org12 模板，org13 看不到；
 *  4) 留痕：建模板+加项+发布+实例化 = 4 条，链校验通过。
 *
 * 设计依据：D1-2/D1-6（模板/实例化）、D1-7（风险评估·模板库）、D2-5。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "grc.scheduler.enabled=false")
@Testcontainers
class TemplateLibraryTest {

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
    private AssessmentItemService itemService;

    @Autowired
    private ControlService controlService;

    @Autowired
    private HashChainService hashChainService;

    private static final long ORG_PAY = 12L;
    private static final long ORG_CF = 13L;

    @BeforeEach
    void clean() throws Exception {
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement()) {
            s.executeUpdate("TRUNCATE assessment_item, assessment_template_item, assessment_template, "
                    + "control_framework_ref, control_item, operation_log RESTART IDENTITY CASCADE");
            s.executeUpdate("DELETE FROM assessment WHERE id >= 1000");
        }
    }

    @AfterEach
    void clearContext() {
        IsolationContext.clear();
    }

    @Test
    void 模板全流程_建项发布实例化为评估项并逐项评估() {
        // 控件库建一个控制项，供模板项引用（演示评估-控件复用）
        Long ctlId = asOrg(ORG_PAY, () -> controlService.create(ORG_PAY, "CTL-ACL", "访问控制", "x", "访问控制", null, "c").getId());

        Long tplId = asOrg(ORG_PAY, () -> templateService.create(ORG_PAY, "TPL-ISO", "ISO 基线",
                ControlFramework.ISO27001, "ISO 27001 自评模板", "owner", "c").getId());
        asOrg(ORG_PAY, () -> templateService.addItem(tplId, ctlId, "A.9.2.3", "最小授权", "c"));
        asOrg(ORG_PAY, () -> templateService.addItem(tplId, null, "A.12.4.1", "日志留存", "c"));

        seedActiveForm(ORG_PAY, tplId);   // B21：发布前提——已启用评估表单
        assertEquals(TemplateStatus.PUBLISHED, asOrg(ORG_PAY, () -> templateService.publish(tplId, "c").getStatus()));

        // 实例化 → 评估 + 2 个评估项（第 1 项复用控件 ctlId）
        Assessment a = asOrg(ORG_PAY, () -> templateService.instantiate(tplId, "2026 ISO 自评", "assessor", "2026Q2", "c"));
        List<AssessmentItem> items = asOrg(ORG_PAY, () -> itemService.listByAssessment(a.getId()));
        assertEquals(2, items.size(), "应按模板项数生成 2 个评估项");
        assertEquals(ctlId, items.get(0).getControlId(), "评估项应引用模板项的控制项（复用）");
        assertEquals(AssessmentItemResult.PENDING, items.get(0).getResult(), "实例化后评估项默认待评");

        // 逐项评估
        Long itemId = items.get(0).getId();
        assertEquals(AssessmentItemResult.CONFORMING,
                asOrg(ORG_PAY, () -> itemService.assess(itemId, AssessmentItemResult.CONFORMING, "已落实", "assessor").getResult()));
    }

    @Test
    void 发布与实例化门控() {
        Long tplId = asOrg(ORG_PAY, () -> templateService.create(ORG_PAY, "TPL-E", "空模板",
                ControlFramework.MLPS, null, null, "c").getId());
        // 空模板不可发布
        assertThrows(IllegalStateException.class, () -> runAsOrg(ORG_PAY, () -> templateService.publish(tplId, "c")));
        // 未发布不可实例化
        asOrg(ORG_PAY, () -> templateService.addItem(tplId, null, "1.1", "x", "c"));
        assertThrows(IllegalStateException.class,
                () -> runAsOrg(ORG_PAY, () -> templateService.instantiate(tplId, "t", "a", "p", "c")));
    }

    @Test
    void 组织隔离_org12模板org13看不到() {
        asOrg(ORG_PAY, () -> templateService.create(ORG_PAY, "TPL-X", "x", ControlFramework.PCI_DSS, null, null, "c"));
        assertEquals(1, asOrg(ORG_PAY, () -> templateService.list()).size(), "org12 应看到自己的 1 个模板");
        assertTrue(asOrg(ORG_CF, () -> templateService.list()).isEmpty(), "org13 不应看到 org12 的模板");
    }

    @Test
    void 留痕_建模板加项发布实例化共4条() {
        Long tplId = asOrg(ORG_PAY, () -> templateService.create(ORG_PAY, "TPL-H", "H",
                ControlFramework.ISO27001, null, null, "c").getId());     // TEMPLATE_CREATE
        asOrg(ORG_PAY, () -> templateService.addItem(tplId, null, "A.1", "x", "c"));    // TEMPLATE_ADD_ITEM
        seedActiveForm(ORG_PAY, tplId);   // B21：发布前提
        asOrg(ORG_PAY, () -> templateService.publish(tplId, "c"));                      // TEMPLATE_PUBLISH
        asOrg(ORG_PAY, () -> templateService.instantiate(tplId, "t", "a", "2026Q2", "c")); // TEMPLATE_INSTANTIATE

        ChainVerifyResult r = asOrg(ORG_PAY, () -> hashChainService.verify(ORG_PAY));
        assertTrue(r.valid(), "留痕后链应校验通过");
        assertEquals(4, r.count(), "应有 4 条模板操作留痕");
    }

    // ---------- 测试辅助 ----------

    /**
     * M2 深度包 B21：发布须已启用表单——测试用 owner 连接直插一条 ACTIVE 表单（绕 RLS）。
     * 先清该模板既有表单：clean() 不 TRUNCATE template_form，而 RESTART IDENTITY 会让新模板复用 id=1，
     * 与启动时 BuiltinFormBootstrap 为平台模板 #1 装的 ACTIVE 表单撞 uk_template_form_active。
     */
    private void seedActiveForm(long orgId, long tplId) {
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement()) {
            s.executeUpdate("DELETE FROM template_form WHERE template_id = " + tplId);
            s.executeUpdate("INSERT INTO template_form(org_id, template_id, version_no, name, schema_json, status) "
                    + "VALUES (" + orgId + ", " + tplId + ", 1, '测试表单', "
                    + "'{\"sections\":[{\"title\":\"t\",\"fields\":[],\"lists\":[]}]}', 'ACTIVE')");
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

    private void runAsOrg(long orgId, Callable<?> action) throws Exception {
        IsolationContext.set(List.of(orgId));
        try {
            action.call();
        } finally {
            IsolationContext.clear();
        }
    }
}
