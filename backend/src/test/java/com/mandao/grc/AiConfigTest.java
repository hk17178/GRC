package com.mandao.grc;

import com.mandao.grc.modules.ai.AiConfigService;
import com.mandao.grc.modules.ai.AiProviderConfig;
import com.mandao.grc.modules.ai.ConfiguredLlmProvider;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 大模型接入 web 配置测试（真实 PG）。验证：
 *  1) 配置 OPENAI + 密钥：视图掩码（不回显明文）、快照解密还原（加密落库往返）；
 *  2) Provider 运行期按 DB 配置切换（name=openai-compatible、model=所配）；
 *  3) 改配置不重输密钥时密钥保持；
 *  4) provider=LOCAL 回退本地离线。
 *
 * 安全：测试用占位密钥（非真实），仅验证加密/掩码/切换逻辑。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "grc.scheduler.enabled=false")
@Testcontainers
class AiConfigTest {

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

    @Autowired private AiConfigService configService;
    @Autowired private ConfiguredLlmProvider provider;

    @BeforeEach
    void resetConfig() throws Exception {
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement()) {
            s.executeUpdate("UPDATE ai_provider_config SET provider='LOCAL', base_url=NULL, model=NULL, "
                    + "api_key_enc=NULL, key_hint=NULL, enabled=true WHERE id=1");
            // 清掉 V42 白名单种子：本测试用任意模型 id 验证密钥加解密，白名单管控由 AiGovernanceTest 覆盖
            s.executeUpdate("DELETE FROM ai_governance WHERE kind = 'MODEL_WHITELIST'");
        }
    }

    @Test
    void 配置openai_密钥加密存储_视图掩码_快照解密() {
        configService.update(AiProviderConfig.OPENAI, "https://dashscope.example/v1", "qwen-plus",
                2048, true, "sk-test-PLACEHOLDER-9911", "admin");

        AiConfigService.ConfigView v = configService.view();
        assertEquals(AiProviderConfig.OPENAI, v.provider());
        assertEquals("qwen-plus", v.model());
        assertTrue(v.keyConfigured(), "应标记已配置密钥");
        assertTrue(v.keyHint() != null && v.keyHint().endsWith("9911"), "掩码应只露末 4 位");

        // 快照解密往返
        AiConfigService.Snapshot s = configService.snapshot();
        assertEquals("sk-test-PLACEHOLDER-9911", s.apiKey(), "加密落库后应能解密还原");

        // Provider 运行期按 DB 配置切换
        assertEquals("openai-compatible", provider.name());
        assertEquals("qwen-plus", provider.model());
    }

    @Test
    void 改配置不重输密钥_密钥保持() {
        configService.update(AiProviderConfig.OPENAI, "https://x/v1", "m1", 1024, true, "sk-keepme-4321", "admin");
        // 仅改 model，apiKey 传 null
        configService.update(AiProviderConfig.OPENAI, "https://x/v1", "m2", 1024, true, null, "admin");

        assertEquals("m2", configService.view().model());
        assertTrue(configService.view().keyConfigured(), "未重输密钥应保持已配置");
        assertEquals("sk-keepme-4321", configService.snapshot().apiKey(), "密钥应保持不变");
    }

    @Test
    void provider_LOCAL_回退本地离线() {
        configService.update(AiProviderConfig.LOCAL, null, null, 1024, true, null, "admin");
        assertEquals("local", provider.name(), "LOCAL 应回退本地离线 Provider");
        assertFalse(configService.view().keyConfigured());
    }
}
