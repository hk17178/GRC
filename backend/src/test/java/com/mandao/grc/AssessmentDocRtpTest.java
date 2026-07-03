package com.mandao.grc;

import com.mandao.grc.common.isolation.IsolationContext;
import com.mandao.grc.modules.assessment.Assessment;
import com.mandao.grc.modules.assessment.AssessmentDoc;
import com.mandao.grc.modules.assessment.AssessmentDocService;
import com.mandao.grc.modules.assessment.AssessmentService;
import com.mandao.grc.modules.assessment.RiskFinding;
import com.mandao.grc.modules.assessment.RiskFindingService;
import com.mandao.grc.modules.assessment.RiskLevel;
import com.mandao.grc.modules.assessment.RiskTreatment;
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
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 风险 R3 集成测试（V51）：过程文档中心 + RTP。验证：
 *  1) 过程文档 上传（sha256 固化）→ 清单 → 删除；
 *  2) RTP upsert（一发现一计划，重复 upsert 更新同一条）；
 *  3) RTP 汇总导出为非空 .docx（PK 头）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "grc.scheduler.enabled=false")
@Testcontainers
class AssessmentDocRtpTest {

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
    private AssessmentDocService docService;
    @Autowired
    private AssessmentService assessmentService;
    @Autowired
    private RiskFindingService findingService;

    private static final long ORG_PAY = 12L;

    @BeforeEach
    void clean() throws Exception {
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement()) {
            s.executeUpdate("TRUNCATE assessment_doc, risk_treatment, risk_finding, assessment CASCADE");
        }
    }

    @AfterEach
    void clearContext() {
        IsolationContext.clear();
    }

    @Test
    void 过程文档_上传固化指纹_清单_删除() {
        Assessment a = asOrg(ORG_PAY, () -> assessmentService.create(ORG_PAY, "文档评估", "u", "2026", "c"));
        byte[] data = "评估实施方案 v1".getBytes(StandardCharsets.UTF_8);
        AssessmentDoc d = asOrg(ORG_PAY, () -> docService.upload(a.getId(), "PLAN", "评估计划书",
                "plan.txt", "text/plain", data, "c"));
        assertEquals(64, d.getSha256().length(), "sha256 应固化");

        List<AssessmentDoc> list = asOrg(ORG_PAY, () -> docService.listDocs(a.getId()));
        assertEquals(1, list.size());
        assertEquals("评估计划书", list.get(0).getName());

        asOrg(ORG_PAY, () -> { docService.deleteDoc(d.getId(), "c"); return null; });
        assertTrue(asOrg(ORG_PAY, () -> docService.listDocs(a.getId())).isEmpty());
    }

    @Test
    void RTP_upsert同条_汇总导出docx() {
        Assessment a = asOrg(ORG_PAY, () -> assessmentService.create(ORG_PAY, "RTP 评估", "u", "2026", "c"));
        RiskFinding f = asOrg(ORG_PAY, () ->
                findingService.createFinding(ORG_PAY, a.getId(), "弱口令风险", RiskLevel.HIGH, "c"));

        asOrg(ORG_PAY, () -> docService.upsertTreatment(f.getId(), "强制复杂度策略+双因子",
                "张三", LocalDate.of(2026, 9, 30), "安全预算 5 万", RiskLevel.LOW, "IN_PROGRESS", "c"));
        // 重复 upsert 更新同一条
        RiskTreatment t2 = asOrg(ORG_PAY, () -> docService.upsertTreatment(f.getId(), "强制复杂度策略+双因子+堡垒机",
                "张三", LocalDate.of(2026, 9, 30), "安全预算 8 万", RiskLevel.LOW, "IN_PROGRESS", "c"));
        List<RiskTreatment> list = asOrg(ORG_PAY, () -> docService.listTreatments(a.getId()));
        assertEquals(1, list.size(), "一发现一计划，重复 upsert 不增行");
        assertEquals("安全预算 8 万", list.get(0).getResource());
        assertEquals(t2.getId(), list.get(0).getId());

        byte[] docx = asOrg(ORG_PAY, () -> docService.buildRtpDocx(a.getId()));
        assertTrue(docx.length > 1000, "RTP docx 应非空");
        assertEquals(0x50, docx[0]);
        assertEquals(0x4B, docx[1]);
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
