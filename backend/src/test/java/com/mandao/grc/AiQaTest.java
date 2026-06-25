package com.mandao.grc;

import com.mandao.grc.common.isolation.IsolationContext;
import com.mandao.grc.modules.ai.AiQaService;
import com.mandao.grc.modules.ai.KbSourceType;
import com.mandao.grc.modules.ai.KnowledgeBaseService;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 检索增强问答（RAG）集成测试（真实 PG + pgvector + 本地离线 LLM）。验证：
 *  1) 问答返回回答 + 引用，引用指向最相关片段；本地模式 provider=local 且诚实标注未接大模型；
 *  2) 组织隔离：org13 对 org12 知识提问，召回为空、回答为「未检索到」。
 *
 * 设计依据：需求 CR-004（AI 接入 · RAG）、D2-5（不展示后端没有的能力假象）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "grc.scheduler.enabled=false")
@Testcontainers
class AiQaTest {

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
    private KnowledgeBaseService kb;
    @Autowired
    private AiQaService qa;

    private static final long ORG_PAY = 12L;
    private static final long ORG_CF = 13L;

    private static final String KYC = "反洗钱 KYC 客户身份识别：开户须核验有效证件，识别受益所有人，留存身份信息。";
    private static final String TLS = "数据安全 传输加密：核心支付系统须启用 TLS1.2 以上协议，密钥定期轮换。";

    @BeforeEach
    void clean() throws Exception {
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement()) {
            s.executeUpdate("TRUNCATE kb_document, kb_chunk RESTART IDENTITY CASCADE");
        }
    }

    @AfterEach
    void clearContext() {
        IsolationContext.clear();
    }

    @Test
    void 问答_返回回答与引用_本地模式诚实标注() {
        asOrg(ORG_PAY, () -> kb.ingest(ORG_PAY, "AML 制度", KbSourceType.POLICY, "POL-AML-1", KYC + "\n\n" + TLS));

        AiQaService.AiAnswer ans = asOrg(ORG_PAY, () -> qa.ask("如何做客户身份识别与受益所有人核验？", 3));
        assertEquals("local", ans.provider(), "默认应为本地离线模式");
        assertTrue(ans.answer().contains("本地离线模式"), "本地模式须诚实标注未接大模型");
        assertFalse(ans.citations().isEmpty(), "应带引用");
        assertTrue(ans.citations().get(0).snippet().contains("身份识别"),
                "首条引用应为最相关的 KYC 片段，实际：" + ans.citations().get(0).snippet());
    }

    @Test
    void 隔离_org13问org12知识_召回为空() {
        asOrg(ORG_PAY, () -> kb.ingest(ORG_PAY, "仅支付可见", KbSourceType.MANUAL, null, KYC));

        AiQaService.AiAnswer ans = asOrg(ORG_CF, () -> qa.ask("客户身份识别", 5));
        assertTrue(ans.citations().isEmpty(), "org13 不应召回 org12 的知识");
        assertTrue(ans.answer().contains("未在知识库检索到"), "无召回应回「未检索到」，实际：" + ans.answer());
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
