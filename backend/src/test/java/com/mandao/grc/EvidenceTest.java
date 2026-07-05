package com.mandao.grc;

import com.mandao.grc.common.isolation.IsolationContext;
import com.mandao.grc.modules.audit.management.AuditFindingService;
import com.mandao.grc.modules.audit.management.AuditPlan;
import com.mandao.grc.modules.audit.management.AuditPlanService;
import com.mandao.grc.modules.audit.management.AuditSeverity;
import com.mandao.grc.modules.audit.management.AuditType;
import com.mandao.grc.modules.audit.management.Evidence;
import com.mandao.grc.modules.audit.management.EvidenceService;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 证据库集成测试（V44）。验证：
 *  1) 上传固化 sha256 → 反向取证校验通过并回溯关联对象；库内被篡改后校验失败；
 *  2) 无关联对象上传被拒；
 *  3) 卷宗导出产出非空 .docx（PK 头）；
 *  4) 隔离：org13 看不到 org12 的证据。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "grc.scheduler.enabled=false")
@Testcontainers
class EvidenceTest {

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
    private EvidenceService evidenceService;
    @Autowired
    private AuditPlanService planService;
    @Autowired
    private AuditFindingService findingService;

    private static final long ORG_PAY = 12L;
    private static final long ORG_CF = 13L;

    @BeforeEach
    void clean() throws Exception {
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement()) {
            s.executeUpdate("TRUNCATE evidence, remediation_order, audit_finding, audit_plan, operation_log CASCADE");
        }
    }

    @AfterEach
    void clearContext() {
        IsolationContext.clear();
    }

    @Test
    void 上传固化指纹_反向取证_篡改可发现() throws Exception {
        AuditPlan plan = asOrg(ORG_PAY, () ->
                planService.create(ORG_PAY, "等保内审", AuditType.INTERNAL, LocalDate.now(), "c"));
        byte[] data = "防火墙策略导出 2026-07".getBytes(StandardCharsets.UTF_8);
        Evidence e = asOrg(ORG_PAY, () -> evidenceService.upload(ORG_PAY, plan.getId(), null, null,
                "防火墙策略", "fw.txt", "text/plain", data, "auditor"));

        EvidenceService.VerifyResult ok = asOrg(ORG_PAY, () -> evidenceService.verify(e.getId()));
        assertTrue(ok.intact(), "未篡改应校验通过");
        assertEquals("等保内审", ok.planTitle(), "应回溯到关联计划");

        // 库内篡改文件字节 → 指纹不再匹配
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement()) {
            s.executeUpdate("UPDATE evidence SET data = decode('DEADBEEF','hex') WHERE id = " + e.getId());
        }
        EvidenceService.VerifyResult bad = asOrg(ORG_PAY, () -> evidenceService.verify(e.getId()));
        assertFalse(bad.intact(), "篡改后校验应失败");
    }

    @Test
    void 无关联对象_上传被拒() {
        assertThrows(IllegalArgumentException.class, () -> asOrg(ORG_PAY, () ->
                evidenceService.upload(ORG_PAY, null, null, null, "孤儿证据", "x.txt", "text/plain",
                        "x".getBytes(StandardCharsets.UTF_8), "c")));
    }

    @Test
    void 卷宗导出_产出docx() {
        AuditPlan plan = asOrg(ORG_PAY, () ->
                planService.create(ORG_PAY, "支付系统审计", AuditType.INTERNAL, LocalDate.now(), "c"));
        asOrg(ORG_PAY, () -> findingService.createFinding(ORG_PAY, plan.getId(), "访问控制缺陷", AuditSeverity.HIGH, "c"));
        asOrg(ORG_PAY, () -> evidenceService.upload(ORG_PAY, plan.getId(), null, null,
                "截图", "s.png", "image/png", new byte[]{1, 2, 3}, "c"));

        byte[] docx = asOrg(ORG_PAY, () -> evidenceService.buildDossier(plan.getId()));
        assertTrue(docx.length > 1000, "卷宗应非空");
        assertEquals(0x50, docx[0], "docx 应为 zip（PK 头）");
        assertEquals(0x4B, docx[1]);
    }

    @Test
    void 卷宗打包zip_含docx与证据原件() throws Exception {
        AuditPlan plan = asOrg(ORG_PAY, () ->
                planService.create(ORG_PAY, "打包审计", AuditType.INTERNAL, LocalDate.now(), "c"));
        asOrg(ORG_PAY, () -> evidenceService.upload(ORG_PAY, plan.getId(), null, null,
                "原件", "policy.txt", "text/plain", "内容".getBytes(StandardCharsets.UTF_8), "c"));

        // 架构治理包 B31：流式打包——写进 ByteArrayOutputStream 后解 zip 校验条目
        java.io.ByteArrayOutputStream sink = new java.io.ByteArrayOutputStream();
        asOrg(ORG_PAY, () -> { evidenceService.streamDossierZip(plan.getId(), sink); return null; });
        byte[] zipBytes = sink.toByteArray();
        java.util.List<String> entries = new java.util.ArrayList<>();
        try (java.util.zip.ZipInputStream zin = new java.util.zip.ZipInputStream(
                new java.io.ByteArrayInputStream(zipBytes), StandardCharsets.UTF_8)) {
            java.util.zip.ZipEntry en;
            while ((en = zin.getNextEntry()) != null) {
                entries.add(en.getName());
            }
        }
        assertTrue(entries.stream().anyMatch(n -> n.endsWith(".docx")), "zip 应含卷宗 docx，实际：" + entries);
        assertTrue(entries.stream().anyMatch(n -> n.startsWith("evidence/EV-") && n.endsWith("policy.txt")),
                "zip 应含证据原件，实际：" + entries);
    }

    @Test
    void 隔离_org13不可见org12证据() {
        AuditPlan plan = asOrg(ORG_PAY, () ->
                planService.create(ORG_PAY, "P", AuditType.INTERNAL, LocalDate.now(), "c"));
        asOrg(ORG_PAY, () -> evidenceService.upload(ORG_PAY, plan.getId(), null, null,
                "E", "e.txt", "text/plain", new byte[]{9}, "c"));
        assertTrue(asOrg(ORG_CF, () -> evidenceService.list(null, null, null)).isEmpty());
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
