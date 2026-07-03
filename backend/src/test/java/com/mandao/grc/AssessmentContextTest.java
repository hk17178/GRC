package com.mandao.grc;

import com.mandao.grc.common.isolation.IsolationContext;
import com.mandao.grc.modules.assessment.Assessment;
import com.mandao.grc.modules.assessment.AssessmentService;
import com.mandao.grc.modules.assessment.form.AssessmentFormService;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 风险评估 R1 · 背景建立集成测试（V46，ISO 27005/GB/T 20984 ①阶段）。验证：
 *  1) setContext 写入 范围/目的/依据/方法/准则/评估组/起止 并可读回；
 *  2) 终态冻结：COMPLETED 后修改背景被拒（报告定稿即冻结）；
 *  3) 保留占位符映射：contextPlaceholders 产出 ${评估范围} 等键值，方法码转中文，空值给"—"。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "grc.scheduler.enabled=false")
@Testcontainers
class AssessmentContextTest {

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
    private AssessmentService service;

    private static final long ORG_PAY = 12L;

    @BeforeEach
    void clean() throws Exception {
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement()) {
            s.executeUpdate("TRUNCATE risk_finding, assessment_answer, assessment CASCADE");
        }
    }

    @AfterEach
    void clearContext() {
        IsolationContext.clear();
    }

    @Test
    void 背景建立_写入读回() {
        Assessment a = asOrg(ORG_PAY, () -> service.create(ORG_PAY, "支付核心系统年度评估", "张三", "2026", "c"));
        Assessment saved = asOrg(ORG_PAY, () -> service.setContext(a.getId(),
                "支付核心网关及其数据库、机房 A 区", "满足等保三级年度自评与内部风控要求",
                "GBT20984,MLPS", "INTERVIEW,TOOL_SCAN",
                "可能性五级×影响五级矩阵；高/极高须管理层签批", "张三、李四、外部顾问王五",
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31), "c"));

        assertEquals("支付核心网关及其数据库、机房 A 区", saved.getScope());
        assertEquals("GBT20984,MLPS", saved.getBasis());
        assertEquals("INTERVIEW,TOOL_SCAN", saved.getMethods());
        assertEquals(LocalDate.of(2026, 7, 1), saved.getStartDate());
        assertEquals("张三、李四、外部顾问王五", asOrg(ORG_PAY, () -> service.get(a.getId())).getTeam());
    }

    @Test
    void 终态冻结_完成后不可改背景() {
        Assessment a = asOrg(ORG_PAY, () -> service.create(ORG_PAY, "T", "u", "2026", "c"));
        asOrg(ORG_PAY, () -> service.start(a.getId(), "c"));
        asOrg(ORG_PAY, () -> service.submitForReview(a.getId(), "c"));
        asOrg(ORG_PAY, () -> service.complete(a.getId(), "c"));

        assertThrows(IllegalStateException.class, () -> asOrg(ORG_PAY, () ->
                        service.setContext(a.getId(), "改范围", null, null, null, null, null, null, null, "c")),
                "已完成评估的背景应冻结");
    }

    @Test
    void 保留占位符映射_方法码转中文_空值兜底() {
        Assessment a = asOrg(ORG_PAY, () -> service.create(ORG_PAY, "占位符评估", "王五", "2026", "c"));
        asOrg(ORG_PAY, () -> service.setContext(a.getId(),
                "范围X", null, "ISO27001", "INTERVIEW,PENTEST", null, null,
                LocalDate.of(2026, 1, 1), null, "c"));

        Map<String, Object> ph = AssessmentFormService.contextPlaceholders(
                asOrg(ORG_PAY, () -> service.get(a.getId())));
        assertEquals("范围X", ph.get("评估范围"));
        assertEquals("—", ph.get("评估目的"), "空值应兜底为 —");
        assertEquals("人员访谈、渗透测试", ph.get("评估方法"), "方法码应转中文顿号串");
        assertEquals("王五", ph.get("评估人"));
        assertEquals("2026-01-01 ~ ", ph.get("评估期间"));
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
