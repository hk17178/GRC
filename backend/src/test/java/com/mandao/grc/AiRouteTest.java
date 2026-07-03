package com.mandao.grc;

import com.mandao.grc.modules.ai.AiConfigService;
import com.mandao.grc.modules.ai.AiGovernance;
import com.mandao.grc.modules.ai.AiGovernanceService;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 模型分配（V49 场景路由）集成测试。验证：
 *  1) 路由清单恒含四场景（未配置给默认停用行）；
 *  2) 保存路由（密钥加密、掩码）→ snapshotFor 取路由配置；停用/未配置回退全局；
 *  3) LOCAL 路由=显式本地（即使全局接了大模型，该场景仍走本地）；
 *  4) 白名单管控对路由同样生效。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "grc.scheduler.enabled=false")
@Testcontainers
class AiRouteTest {

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
    private AiConfigService configService;
    @Autowired
    private AiGovernanceService governance;

    @BeforeEach
    void clean() throws Exception {
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement()) {
            s.executeUpdate("DELETE FROM ai_model_route");
            s.executeUpdate("DELETE FROM ai_governance WHERE kind = 'MODEL_WHITELIST'");
            s.executeUpdate("UPDATE ai_provider_config SET provider='OPENAI', base_url='http://global', "
                    + "model='global-model', api_key_enc=NULL, key_hint=NULL, enabled=true WHERE id=1");
        }
    }

    @Test
    void 路由清单恒四场景_保存后快照走路由_停用回退全局() {
        List<AiConfigService.RouteView> init = configService.listRoutes();
        assertEquals(4, init.size(), "恒含 QA/MATERIAL/REG_SUMMARY/POLICY_MAP 四场景");
        assertTrue(init.stream().noneMatch(AiConfigService.RouteView::enabled), "初始全部停用");

        // 保存 QA 路由（带密钥）→ 快照取路由
        configService.updateRoute("QA", "OPENAI", "http://qa-model", "qwen-plus", 2048, true, "sk-test-1234", "c");
        AiConfigService.Snapshot qa = configService.snapshotFor("QA");
        assertEquals("http://qa-model", qa.baseUrl(), "启用路由后场景快照应取路由配置");
        assertEquals("qwen-plus", qa.model());
        assertEquals("sk-test-1234", qa.apiKey(), "路由密钥应解密可用");
        // 掩码回显
        AiConfigService.RouteView v = configService.listRoutes().stream()
                .filter(r -> "QA".equals(r.scenario())).findFirst().orElseThrow();
        assertTrue(v.keyConfigured());
        assertEquals("····1234", v.keyHint(), "掩码只留末 4 位");

        // 其他场景未配置 → 回退全局
        assertEquals("global-model", configService.snapshotFor("MATERIAL").model(), "未配置场景应回退全局");

        // 停用 → 回退全局
        configService.updateRoute("QA", "OPENAI", "http://qa-model", "qwen-plus", 2048, false, null, "c");
        assertEquals("global-model", configService.snapshotFor("QA").model(), "停用后应回退全局");
    }

    @Test
    void LOCAL路由_显式本地_白名单管控生效() {
        // LOCAL 路由：即使全局是 OPENAI，该场景显式走本地
        configService.updateRoute("REG_SUMMARY", "LOCAL", null, null, null, true, null, "c");
        assertEquals("LOCAL", configService.snapshotFor("REG_SUMMARY").provider());

        // 白名单管控：加白名单后，非白名单模型的路由保存被拒
        governance.create(AiGovernance.KIND_MODEL_WHITELIST, "qwen-plus", null, "c");
        assertThrows(IllegalArgumentException.class, () ->
                configService.updateRoute("MATERIAL", "OPENAI", "http://x", "gpt-4o-mini", null, true, "sk-x", "c"));
    }
}
