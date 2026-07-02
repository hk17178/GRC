package com.mandao.grc;

import com.mandao.grc.common.isolation.IsolationContext;
import com.mandao.grc.modules.ai.AiMaterialService;
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
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AI 生成报送/汇报材料集成测试（真实 PG + 本地离线 LLM）。验证需求 7.5.1：
 *  1) 两类材料（监管报送稿 / 管理层简报）都能生成，草稿非空；
 *  2) 产出恒带 needsReview=true（人工复核标记）与 dataAsOf 数据时点；
 *  3) 本地离线模式下 provider=local，草稿诚实标注未接大模型（不假装生成式产出）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "grc.scheduler.enabled=false")
@Testcontainers
class AiMaterialTest {

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
    private AiMaterialService materialService;

    private static final long ORG_PAY = 12L;

    @AfterEach
    void clearContext() {
        IsolationContext.clear();
    }

    @Test
    void 管理层简报_生成_带复核标记与数据时点() {
        AiMaterialService.Material m = asOrg(ORG_PAY, () -> materialService.generate("MGMT_BRIEF"));
        assertEquals("MGMT_BRIEF", m.type());
        assertNotNull(m.draft());
        assertFalse(m.draft().isBlank(), "草稿不应为空");
        assertTrue(m.needsReview(), "AI 产出须恒带人工复核标记");
        assertNotNull(m.dataAsOf(), "须标注数据时点");
        assertEquals("local", m.provider(), "默认应为本地离线模式");
        assertTrue(m.draft().contains("本地离线模式"), "本地模式草稿须诚实标注未接大模型");
    }

    @Test
    void 监管报送稿_生成_类型正确() {
        AiMaterialService.Material m = asOrg(ORG_PAY, () -> materialService.generate("FILING_DRAFT"));
        assertEquals("FILING_DRAFT", m.type());
        assertFalse(m.draft().isBlank(), "草稿不应为空");
        assertTrue(m.needsReview());
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
