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
            s.executeUpdate("TRUNCATE kb_document, kb_chunk, ai_feedback RESTART IDENTITY CASCADE");
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

    @Test
    void a16_检索去重_单文档不霸榜引用来源多样() {
        // 一篇长文档切成多块（全 KYC 主题），另一篇少量 TLS 主题
        String bigKyc = KYC + "\n\n" + KYC + "\n\n" + KYC + "\n\n" + KYC + "\n\n" + KYC;
        asOrg(ORG_PAY, () -> kb.ingest(ORG_PAY, "AML 大全", KbSourceType.POLICY, "POL-AML-BIG", bigKyc));
        asOrg(ORG_PAY, () -> kb.ingest(ORG_PAY, "加密制度", KbSourceType.POLICY, "POL-TLS", TLS));

        AiQaService.AiAnswer ans = asOrg(ORG_PAY, () -> qa.ask("客户身份识别", 4));
        long distinctDocs = ans.citations().stream().map(AiQaService.Citation::documentId).distinct().count();
        long perDocMax = ans.citations().stream()
                .collect(java.util.stream.Collectors.groupingBy(AiQaService.Citation::documentId,
                        java.util.stream.Collectors.counting()))
                .values().stream().mapToLong(Long::longValue).max().orElse(0);
        assertTrue(perDocMax <= 2, "A16：单文档引用不应超过 2 块，实际最多 " + perDocMax);
        assertTrue(distinctDocs >= 1, "应有引用");
    }

    @Test
    void b26_多轮追问_历史随请求送入不报错() {
        asOrg(ORG_PAY, () -> kb.ingest(ORG_PAY, "AML 制度", KbSourceType.POLICY, "POL-AML-1", KYC));
        var history = List.of(new AiQaService.Turn("什么是受益所有人？", "指最终拥有或控制客户的自然人。"));
        // 指代性追问「那要怎么核验它？」——历史送入不应报错，仍返回带引用的回答
        AiQaService.AiAnswer ans = asOrg(ORG_PAY, () -> qa.ask("那要怎么核验它？", 3, history));
        assertFalse(ans.citations().isEmpty(), "带历史的追问仍应正常召回作答");
    }

    @Test
    void b32_反馈落库_受组织隔离() {
        asOrg(ORG_PAY, () -> {
            qa.recordFeedback("客户身份识别怎么做？", "须核验证件……", true, null, "tester");
            return null;
        });
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement();
             var rs = s.executeQuery("SELECT count(*) FROM ai_feedback WHERE org_id = 12 AND helpful = true")) {
            rs.next();
            assertEquals(1, rs.getInt(1), "赞反馈应落 org12 一条");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
