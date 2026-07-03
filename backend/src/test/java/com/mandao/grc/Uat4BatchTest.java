package com.mandao.grc;

import com.mandao.grc.common.isolation.IsolationContext;
import com.mandao.grc.modules.assessment.Assessment;
import com.mandao.grc.modules.assessment.AssessmentService;
import com.mandao.grc.modules.assessment.AssessmentTemplate;
import com.mandao.grc.modules.assessment.SignatureTicket;
import com.mandao.grc.modules.assessment.SignatureTicketService;
import com.mandao.grc.modules.assessment.TemplateService;
import com.mandao.grc.modules.assessment.form.AssessmentFormService;
import com.mandao.grc.modules.assessment.form.TemplateForm;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Base64;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UAT 四轮批次集成测试（V56-V57）。验证：
 *  1) 内置表单引导：应用就绪后内置模板全部带 ACTIVE 标准表单（开箱即用），
 *     schema 含规范章节（评估概述/资产识别/风险分析）与风险清单明细表；
 *  2) 手机签名令牌全流程：创建→手机提交→桌面取回（一次性 USED）；重复提交/取回被拒；
 *  3) V56 改名：ISO 27001 模板不再叫"内审模板"。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "grc.scheduler.enabled=false")
@Testcontainers
class Uat4BatchTest {

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
    private AssessmentFormService formService;
    @Autowired
    private AssessmentService assessmentService;
    @Autowired
    private SignatureTicketService ticketService;

    private static final long ORG_PAY = 12L;

    @AfterEach
    void clearContext() {
        IsolationContext.clear();
    }

    @Test
    void 内置模板开箱即用_标准章节表单已预装() {
        List<AssessmentTemplate> platform = asOrg(1L, () -> templateService.list().stream()
                .filter(t -> "platform".equals(t.getOwner())).toList());
        assertTrue(platform.size() >= 8, "八体系内置模板应在");

        for (AssessmentTemplate t : platform) {
            List<TemplateForm> forms = asOrg(1L, () -> formService.listForms(t.getId()));
            assertTrue(forms.stream().anyMatch(f -> "ACTIVE".equals(f.getStatus())),
                    "内置模板应预装 ACTIVE 标准表单：" + t.getCode());
        }
        // 章节规范性：任取一模板的 ACTIVE 表单 schema
        TemplateForm active = asOrg(1L, () -> formService.listForms(platform.get(0).getId()).stream()
                .filter(f -> "ACTIVE".equals(f.getStatus())).findFirst().orElseThrow());
        var schema = asOrg(1L, () -> formService.schemaOf(formService.getForm(active.getId())));
        String titles = schema.sections().stream().map(s -> s.title() == null ? "" : s.title())
                .reduce("", (x, y) -> x + "|" + y);
        assertTrue(titles.contains("评估概述"), "应含 评估概述 章节，实际：" + titles);
        assertTrue(titles.contains("资产识别"), "应含 资产识别 章节");
        assertTrue(titles.contains("风险分析与评价"), "应含 风险分析与评价 章节");
        boolean hasRiskList = schema.sections().stream()
                .flatMap(s -> s.lists().stream()).anyMatch(l -> l.key().contains("风险清单"));
        assertTrue(hasRiskList, "应含 风险清单 明细表");
    }

    @Test
    void 手机签名令牌_全流程_一次性() {
        Assessment a = asOrg(ORG_PAY, () -> assessmentService.create(ORG_PAY, "扫码签批评估", "u", "2026", "c"));
        SignatureTicket t = asOrg(ORG_PAY, () -> ticketService.createTicket(a.getId(), "risk_mgr"));
        assertEquals("PENDING", ticketService.ticketInfo(t.getToken()).status());

        // 手机免登录提交（1x1 PNG）
        String dataUrl = "data:image/png;base64," + Base64.getEncoder().encodeToString(new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47});
        ticketService.submitSignature(t.getToken(), dataUrl);
        assertEquals("SIGNED", ticketService.ticketInfo(t.getToken()).status());
        // 重复提交被拒
        assertThrows(IllegalStateException.class, () -> ticketService.submitSignature(t.getToken(), dataUrl));

        // 桌面取回 → USED（一次性）
        var fetched = asOrg(ORG_PAY, () -> ticketService.fetchSignature(a.getId(), t.getToken()));
        assertEquals("SIGNED", fetched.status());
        assertNotNull(fetched.signatureDataUrl());
        var again = asOrg(ORG_PAY, () -> ticketService.fetchSignature(a.getId(), t.getToken()));
        assertEquals("USED", again.status(), "取回后令牌应作废");
    }

    @Test
    void V56改名_ISO模板不再叫内审模板() {
        AssessmentTemplate iso = asOrg(1L, () -> templateService.list().stream()
                .filter(t -> "TPL-ISO27001".equals(t.getCode())).findFirst().orElseThrow());
        assertEquals("ISO/IEC 27001 风险评估模板", iso.getName());
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
