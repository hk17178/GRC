package com.mandao.grc;

import com.mandao.grc.common.isolation.IsolationContext;
import com.mandao.grc.modules.notify.NotifyConfig;
import com.mandao.grc.modules.notify.NotifyConfigService;
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
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 通知中心配置集成测试（真实 PG + 切面 + RLS）。验证：
 *  1) 按 kind 分类增查（场景/规则/通道互不混）；
 *  2) 启停切换；
 *  3) 组织隔离：org12 配置 org13 不可见。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "grc.scheduler.enabled=false")
@Testcontainers
class NotifyConfigTest {

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
    private NotifyConfigService service;
    @Autowired
    private com.mandao.grc.kernel.AlertPushService alertPushService;

    private static final long ORG_PAY = 12L;
    private static final long ORG_CF = 13L;

    @BeforeEach
    void clean() throws Exception {
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement()) {
            s.executeUpdate("TRUNCATE notify_config RESTART IDENTITY CASCADE");
        }
    }

    @AfterEach
    void clearContext() {
        IsolationContext.clear();
    }

    @Test
    void 三类配置分类增查与启停() {
        asOrg(ORG_PAY, () -> service.create(ORG_PAY, NotifyConfig.SCENARIO, "外审计划临近", "{\"trigger\":\"前15天\"}"));
        // A25：RULE 须为合法配置（已知 source + 非空 template）
        NotifyConfig rule = asOrg(ORG_PAY, () -> service.create(ORG_PAY, NotifyConfig.RULE, "整改逾期升级",
                "{\"source\":\"REMEDIATION_OVERDUE\",\"template\":\"整改单{标题}逾期{逾期天数}天\"}"));
        asOrg(ORG_PAY, () -> service.create(ORG_PAY, NotifyConfig.CHANNEL, "企微机器人", "{\"type\":\"WECOM\"}"));

        assertEquals(1, asOrg(ORG_PAY, () -> service.listByKind(NotifyConfig.SCENARIO)).size(), "场景应 1 条");
        assertEquals(1, asOrg(ORG_PAY, () -> service.listByKind(NotifyConfig.RULE)).size(), "规则应 1 条");
        assertEquals(1, asOrg(ORG_PAY, () -> service.listByKind(NotifyConfig.CHANNEL)).size(), "通道应 1 条");

        // 停用规则
        assertFalse(asOrg(ORG_PAY, () -> service.setEnabled(rule.getId(), false)).isEnabled());
    }

    @Test
    void a25_坏规则配置写入即拒() {
        // 未知数据源 → 拒
        assertThrows(IllegalArgumentException.class, () -> asOrg(ORG_PAY, () ->
                service.create(ORG_PAY, NotifyConfig.RULE, "坏源", "{\"source\":\"FOO\",\"template\":\"x\"}")));
        // 空模板 → 拒
        assertThrows(IllegalArgumentException.class, () -> asOrg(ORG_PAY, () ->
                service.create(ORG_PAY, NotifyConfig.RULE, "空模板", "{\"source\":\"REG_NEW\"}")));
        // 非法 JSON → 拒
        assertThrows(IllegalArgumentException.class, () -> asOrg(ORG_PAY, () ->
                service.create(ORG_PAY, NotifyConfig.RULE, "坏JSON", "{not json")));
        // 合法 → 通过
        assertEquals(1, asOrg(ORG_PAY, () -> {
            service.create(ORG_PAY, NotifyConfig.RULE, "好规则",
                    "{\"source\":\"KRI_BREACH\",\"template\":\"{指标}触阈{级别}\"}");
            return service.listByKind(NotifyConfig.RULE).size();
        }));
    }

    @Test
    void d批新数据源规则可创建_白名单已与引擎对齐() {
        // 安全评审后修复：RULE_SOURCES 白名单曾只含 4 源，UI 提供的 6 个新源保存被拒。现须全部接受。
        String[] newSources = {"REG_CHANGE", "ACCOUNT_LOCKED", "UAR_OVERDUE", "COMPLIANCE_DIGEST",
                "ASSESSMENT_UPCOMING", "AUDIT_PLAN_UPCOMING"};
        for (String src : newSources) {
            asOrg(ORG_PAY, () -> service.create(ORG_PAY, NotifyConfig.RULE, "规则-" + src,
                    "{\"source\":\"" + src + "\",\"template\":\"测试模板 {周}\"}"));
        }
        assertEquals(newSources.length, asOrg(ORG_PAY, () -> service.listByKind(NotifyConfig.RULE)).size(),
                "六个新数据源规则均应被 RULE_SOURCES 接受");
    }

    @Test
    void 组织隔离_org12配置org13不可见() {
        asOrg(ORG_PAY, () -> service.create(ORG_PAY, NotifyConfig.SCENARIO, "X", "{}"));
        assertEquals(1, asOrg(ORG_PAY, () -> service.listByKind(NotifyConfig.SCENARIO)).size());
        assertTrue(asOrg(ORG_CF, () -> service.listByKind(NotifyConfig.SCENARIO)).isEmpty(), "org13 不应看到 org12 的场景");
    }

    @Test
    void h3_发送留痕按可见组织隔离_不跨租户() throws Exception {
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement()) {
            s.executeUpdate("DELETE FROM notify_send_log WHERE org_id IN (12, 13)");
            s.executeUpdate("INSERT INTO notify_send_log(org_id, channel_type, target, message, success) VALUES "
                    + "(12, 'WECOM', 'http://a', 'org12留痕', true), "
                    + "(13, 'WECOM', 'http://b', 'org13留痕', true)");
        }
        var r12 = asOrg(ORG_PAY, () -> alertPushService.recentLogs(100));
        assertTrue(r12.stream().allMatch(m -> ((Number) m.get("orgId")).longValue() == 12L),
                "org12 只应看到本组织发送留痕");
        assertTrue(r12.stream().anyMatch(m -> "org12留痕".equals(m.get("message"))), "应看到本组织留痕");
        assertTrue(r12.stream().noneMatch(m -> "org13留痕".equals(m.get("message"))), "不得看到 org13 留痕（H-3 跨租户泄露）");
        // 无可见域上下文 → 空（不泄露）
        assertTrue(alertPushService.recentLogs(100).isEmpty(), "无隔离上下文应返回空");
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
