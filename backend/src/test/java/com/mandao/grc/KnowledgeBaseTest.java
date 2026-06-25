package com.mandao.grc;

import com.mandao.grc.common.isolation.IsolationContext;
import com.mandao.grc.modules.ai.KbDocStatus;
import com.mandao.grc.modules.ai.KbDocument;
import com.mandao.grc.modules.ai.KbSourceType;
import com.mandao.grc.modules.ai.KnowledgeBaseService;
import com.mandao.grc.modules.ai.VectorStore;
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
 * AI 知识库 / 向量检索集成测试（真实 PG + pgvector + 切面 + RLS）。验证：
 *  1) 摄入：切块 + 嵌入 → 文档置 INDEXED 且块数正确；
 *  2) 向量召回：与查询语义相关的块排在前（本地确定性嵌入，共享词越多越相似）；
 *  3) 组织隔离：org12 的知识，org13 既列不到也召回不到（RLS 兜底）。
 *
 * 设计依据：需求 CR-004（AI 接入 · RAG）、D2-5。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "grc.scheduler.enabled=false")
@Testcontainers
class KnowledgeBaseTest {

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

    private static final long ORG_PAY = 12L;
    private static final long ORG_CF = 13L;

    // 两段语义不同的内容：反洗钱/KYC vs 数据加密
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
    void 摄入_切块嵌入_文档置INDEXED() {
        KbDocument doc = asOrg(ORG_PAY, () -> kb.ingest(ORG_PAY, "AML 制度", KbSourceType.POLICY, "POL-AML-1",
                KYC + "\n\n" + TLS));
        assertEquals(KbDocStatus.INDEXED, doc.getStatus(), "摄入后应为 INDEXED");
        assertEquals(2, doc.getChunkCount(), "两段应切成 2 块");
        assertEquals(2, asOrg(ORG_PAY, () -> kb.chunks(doc.getId())).size());
    }

    @Test
    void 向量召回_相关块排前() {
        asOrg(ORG_PAY, () -> kb.ingest(ORG_PAY, "AML 制度", KbSourceType.POLICY, "POL-AML-1", KYC + "\n\n" + TLS));

        List<VectorStore.ChunkHit> hits =
                asOrg(ORG_PAY, () -> kb.search("客户身份识别与受益所有人核验", 2));
        assertFalse(hits.isEmpty(), "应有召回");
        assertTrue(hits.get(0).content().contains("身份识别"),
                "与 KYC 查询最相关的块应排第一，实际：" + hits.get(0).content());
    }

    @Test
    void 组织隔离_org12知识org13召回为空() {
        asOrg(ORG_PAY, () -> kb.ingest(ORG_PAY, "仅支付可见", KbSourceType.MANUAL, null, KYC));
        assertEquals(1, asOrg(ORG_PAY, () -> kb.list()).size(), "org12 应列到自己的 1 篇");
        assertTrue(asOrg(ORG_CF, () -> kb.list()).isEmpty(), "org13 不应列到 org12 的文档");
        assertTrue(asOrg(ORG_CF, () -> kb.search("客户身份识别", 5)).isEmpty(),
                "org13 不应召回 org12 的切块（RLS 兜底）");
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
}
