package com.mandao.grc;

import com.mandao.grc.common.isolation.IsolationContext;
import com.mandao.grc.modules.ai.AiConfigService;
import com.mandao.grc.modules.ai.AiGovernance;
import com.mandao.grc.modules.ai.AiGovernanceService;
import com.mandao.grc.modules.ai.KnowledgeBaseService;
import com.mandao.grc.modules.ai.KbSourceType;
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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AI 治理集成测试（V42）。验证：
 *  1) 模型白名单管控：有启用条目时，非 LOCAL 且不在白名单的模型保存被拒；命中/LOCAL/停用全部条目则放行；
 *  2) 提示词模板 CRUD：新建/改正文/启停/删除；
 *  3) 知识库文档删除：删除后文档与切块一并消失（连向量）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "grc.scheduler.enabled=false")
@Testcontainers
class AiGovernanceTest {

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
    private AiGovernanceService governance;
    @Autowired
    private AiConfigService configService;
    @Autowired
    private KnowledgeBaseService kb;

    private static final long ORG_PAY = 12L;

    @BeforeEach
    void clean() throws Exception {
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement()) {
            s.executeUpdate("DELETE FROM ai_governance");
            s.executeUpdate("TRUNCATE kb_document, kb_chunk RESTART IDENTITY CASCADE");
            s.executeUpdate("UPDATE ai_provider_config SET provider='LOCAL', model=NULL WHERE id=1");
        }
    }

    @AfterEach
    void clearContext() {
        IsolationContext.clear();
    }

    @Test
    void 模型白名单管控() {
        // 无白名单条目：任意模型放行
        assertDoesNotThrow(() -> configService.update("OPENAI", "http://x", "any-model", 1024, true, null, "c"));

        // 加入白名单：只允许 qwen-plus
        AiGovernance w = governance.create(AiGovernance.KIND_MODEL_WHITELIST, "qwen-plus", "允许", "c");
        assertThrows(IllegalArgumentException.class,
                () -> configService.update("OPENAI", "http://x", "gpt-4o-mini", 1024, true, null, "c"),
                "不在白名单的模型应被拒");
        assertDoesNotThrow(() -> configService.update("OPENAI", "http://x", "qwen-plus", 1024, true, null, "c"),
                "白名单内模型放行");
        assertDoesNotThrow(() -> configService.update("LOCAL", null, null, 1024, true, null, "c"),
                "LOCAL 离线模式不校验");

        // 停用唯一条目 = 未启用管控 → 放行
        governance.setEnabled(w.getId(), false);
        assertDoesNotThrow(() -> configService.update("OPENAI", "http://x", "gpt-4o-mini", 1024, true, null, "c"));
    }

    @Test
    void 提示词模板CRUD() {
        AiGovernance p = governance.create(AiGovernance.KIND_PROMPT_TEMPLATE, "变更摘要", "请摘要…", "c");
        governance.update(p.getId(), "变更摘要", "请按条款摘要…", "c2");
        List<AiGovernance> list = governance.listByKind(AiGovernance.KIND_PROMPT_TEMPLATE);
        assertEquals(1, list.size());
        assertEquals("请按条款摘要…", list.get(0).getDetail());
        governance.delete(p.getId());
        assertTrue(governance.listByKind(AiGovernance.KIND_PROMPT_TEMPLATE).isEmpty());
    }

    @Test
    void 知识库文档删除_连切块() {
        Long docId = asOrg(ORG_PAY, () ->
                kb.ingest(ORG_PAY, "待删文档", KbSourceType.MANUAL, null, "第一段。\n\n第二段。")).getId();
        assertEquals(2, asOrg(ORG_PAY, () -> kb.chunks(docId)).size());

        asOrg(ORG_PAY, () -> { kb.delete(docId); return null; });
        assertTrue(asOrg(ORG_PAY, () -> kb.list()).isEmpty(), "文档应已删除");
        assertTrue(asOrg(ORG_PAY, () -> kb.chunks(docId)).isEmpty(), "切块应一并删除");
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
