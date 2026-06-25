package com.mandao.grc;

import com.mandao.grc.common.isolation.IsolationContext;
import com.mandao.grc.modules.ai.AiQaService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 通用 OpenAI-兼容 Provider「切换装配」测试（完全离线，不需密钥/外网）。
 *
 * 验证：把 grc.ai.provider 切到 openai 后——(1) 生效的 LlmProvider 即 OpenAI-兼容实现（provider=openai-compatible、
 * 暴露所配模型名）；(2) 调用到不可达端点时优雅降级为可读错误，不抛栈。
 * 即证明「解除单一厂商锁定、可切到任意 OpenAI 兼容模型」的装配链路真实可用；真实远端调用属配置驱动，按设计不在测试内联网。
 *
 * 设计依据：用户增强诉求①（通用大模型 API Key 接入）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "grc.scheduler.enabled=false",
                "grc.ai.provider=openai",
                "grc.ai.openai.base-url=http://127.0.0.1:1/v1",
                "grc.ai.openai.api-key=dummy-not-a-real-key",
                "grc.ai.openai.model=qwen-plus"
        })
@Testcontainers
class OpenAiProviderWiringTest {

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
    private AiQaService qa;

    @AfterEach
    void clearContext() {
        IsolationContext.clear();
    }

    @Test
    void 切到openai_装配通用provider_暴露模型名() {
        assertEquals("openai-compatible", qa.provider(), "provider=openai 应装配通用 OpenAI-兼容实现");
        assertEquals("qwen-plus", qa.model(), "应暴露所配模型名（证明可接任意 OpenAI 兼容模型）");
    }

    @Test
    void 端点不可达_优雅降级不抛栈() {
        IsolationContext.set(List.of(12L));
        try {
            AiQaService.AiAnswer ans = qa.ask("测试通用大模型接入", 2);
            assertTrue(ans.answer().contains("调用大模型失败"),
                    "不可达端点应优雅降级为可读错误，实际：" + ans.answer());
        } finally {
            IsolationContext.clear();
        }
    }
}
