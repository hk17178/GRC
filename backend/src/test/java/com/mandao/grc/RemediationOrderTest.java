package com.mandao.grc;

import com.mandao.grc.common.isolation.IsolationContext;
import com.mandao.grc.modules.audit.HashChainService;
import com.mandao.grc.modules.audit.management.AuditFindingService;
import com.mandao.grc.modules.audit.management.AuditFindingStatus;
import com.mandao.grc.modules.audit.management.AuditPlanService;
import com.mandao.grc.modules.audit.management.AuditSeverity;
import com.mandao.grc.modules.audit.management.AuditType;
import com.mandao.grc.modules.audit.management.RemediationService;
import com.mandao.grc.modules.audit.management.RemediationStatus;
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
import java.util.concurrent.Callable;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 整改工单 + 验证闭环集成测试（真实 PG + 切面 + RLS）。验证：
 *  1) 验证闭环红线：发现无已验证整改工单不可标记已整改；工单 派单→开始→提交→验证 后可整改并关闭；
 *  2) 工单状态机非法流转被拒（如未提交即验证）；
 *  3) 验证不通过退回返工后可再次提交验证；
 *  4) 组织隔离：org12 工单，org13 看不到；
 *  5) 留痕：全流程哈希链校验通过。
 *
 * 设计依据：需求 M3 审计管理（整改跟踪/验证闭环）、D1-2、D2-5。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "grc.scheduler.enabled=false")
@Testcontainers
class RemediationOrderTest {

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
    private AuditPlanService planService;

    @Autowired
    private AuditFindingService findingService;

    @Autowired
    private RemediationService remediationService;

    @Autowired
    private HashChainService hashChainService;

    private static final long ORG_PAY = 12L;
    private static final long ORG_CF = 13L;
    private static final LocalDate TODAY = LocalDate.of(2026, 6, 24);

    @BeforeEach
    void clean() throws Exception {
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement()) {
            s.executeUpdate("TRUNCATE remediation_order, audit_finding, audit_plan, operation_log RESTART IDENTITY CASCADE");
        }
    }

    @AfterEach
    void clearContext() {
        IsolationContext.clear();
    }

    /** 造一个 INTERNAL 计划下的审计发现并进入 ANALYZING，返回 findingId。 */
    private Long openFinding() {
        Long pid = planService.create(ORG_PAY, "2026 内审", AuditType.INTERNAL, TODAY.plusDays(10), "creator").getId();
        Long fid = findingService.createFinding(ORG_PAY, pid, "权限过宽", AuditSeverity.HIGH, "auditor").getId();
        findingService.analyze(fid, "auditor");
        return fid;
    }

    @Test
    void 验证闭环_无已验证工单不可整改_验证后可整改关闭() {
        Long fid = asOrg(ORG_PAY, this::openFinding);

        // 红线：无已验证工单 → 不能标记已整改
        assertThrows(IllegalStateException.class,
                () -> runAsOrg(ORG_PAY, () -> findingService.remediate(fid, "auditor")));

        // 派单 → 开始 → 提交 → 验证
        Long oid = asOrg(ORG_PAY, () -> remediationService.create(fid, "owner1", TODAY.plusDays(7), "收敛权限", "lead").getId());
        asOrg(ORG_PAY, () -> remediationService.start(oid, "owner1"));
        asOrg(ORG_PAY, () -> remediationService.submit(oid, "已按最小权限重配", "owner1"));
        assertEquals(RemediationStatus.VERIFIED,
                asOrg(ORG_PAY, () -> remediationService.verify(oid, "auditor").getStatus()));

        // 现可标记已整改并关闭
        assertEquals(AuditFindingStatus.REMEDIATED,
                asOrg(ORG_PAY, () -> findingService.remediate(fid, "auditor").getStatus()));
        assertEquals(AuditFindingStatus.CLOSED,
                asOrg(ORG_PAY, () -> findingService.closeFinding(fid, "lead").getStatus()));

        // 留痕全程链校验通过
        assertTrue(asOrg(ORG_PAY, () -> hashChainService.verify(ORG_PAY)).valid(), "全流程留痕链应校验通过");
    }

    @Test
    void 工单状态机_未提交即验证被拒() {
        Long fid = asOrg(ORG_PAY, this::openFinding);
        Long oid = asOrg(ORG_PAY, () -> remediationService.create(fid, "o", TODAY.plusDays(3), "x", "lead").getId());
        // PENDING 态直接验证非法
        assertThrows(IllegalStateException.class,
                () -> runAsOrg(ORG_PAY, () -> remediationService.verify(oid, "auditor")));
        // IN_PROGRESS 态直接验证仍非法（须先 submit）
        asOrg(ORG_PAY, () -> remediationService.start(oid, "o"));
        assertThrows(IllegalStateException.class,
                () -> runAsOrg(ORG_PAY, () -> remediationService.verify(oid, "auditor")));
    }

    @Test
    void 工单退回返工_可再次提交验证() {
        Long fid = asOrg(ORG_PAY, this::openFinding);
        Long oid = asOrg(ORG_PAY, () -> remediationService.create(fid, "o", TODAY.plusDays(3), "x", "lead").getId());
        asOrg(ORG_PAY, () -> remediationService.start(oid, "o"));
        asOrg(ORG_PAY, () -> remediationService.submit(oid, "v1", "o"));
        // 验证不通过 → 退回 IN_PROGRESS
        assertEquals(RemediationStatus.IN_PROGRESS,
                asOrg(ORG_PAY, () -> remediationService.reject(oid, "证据不足", "auditor").getStatus()));
        // 再次提交并验证通过
        asOrg(ORG_PAY, () -> remediationService.submit(oid, "v2", "o"));
        assertEquals(RemediationStatus.VERIFIED,
                asOrg(ORG_PAY, () -> remediationService.verify(oid, "auditor").getStatus()));
    }

    @Test
    void 组织隔离_org12工单org13看不到() {
        Long fid = asOrg(ORG_PAY, this::openFinding);
        Long oid = asOrg(ORG_PAY, () -> remediationService.create(fid, "o", TODAY.plusDays(3), "x", "lead").getId());
        // org13 上下文取不到 org12 的工单（视为不存在）
        assertThrows(IllegalArgumentException.class,
                () -> runAsOrg(ORG_CF, () -> remediationService.get(oid)));
    }

    // ---------- 测试辅助 ----------

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
