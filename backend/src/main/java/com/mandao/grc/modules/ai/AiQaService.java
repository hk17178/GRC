package com.mandao.grc.modules.ai;

import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 检索增强问答服务（RAG）。
 *
 * 流程：对问题做向量召回 top-k 知识片段 → 交由 {@link LlmProvider} 依据片段生成回答 → 连同引用一并返回。
 * 隔离：召回经 {@link KnowledgeBaseService#search}（@Transactional + RLS），只命中可见组织的知识。
 * 八轮 8-2（B7）：每次问答留痕入哈希链（提问人/问题摘要/命中片段数/模型），M5-5 事后审计依据。
 */
@Service
public class AiQaService {

    /** 召回片段摘要最大字符数（用于引用展示）。 */
    private static final int SNIPPET_MAX = 120;
    /** 默认召回条数。 */
    private static final int DEFAULT_TOP_K = 4;

    private final KnowledgeBaseService kb;
    private final LlmProvider llm;

    /** 留痕链（八轮 8-2：AI 访问日志，setter 注入）。 */
    private com.mandao.grc.modules.audit.HashChainService hashChainService;

    @org.springframework.beans.factory.annotation.Autowired
    void wireHashChain(com.mandao.grc.modules.audit.HashChainService hashChainService) {
        this.hashChainService = hashChainService;
    }

    public AiQaService(KnowledgeBaseService kb, LlmProvider llm) {
        this.kb = kb;
        this.llm = llm;
    }

    /** 一条引用（命中片段的可展示形式）。 */
    public record Citation(Long documentId, Integer seq, String snippet, double score) {
    }

    /** 问答结果：回答 + 当前 AI 模式 + 引用列表。 */
    public record AiAnswer(String answer, String provider, List<Citation> citations) {
    }

    /** 提问：召回 → 生成 → 带引用返回；访问留痕入链（M5-5 事后审计依据）。 */
    public AiAnswer ask(String question, Integer topK) {
        int k = (topK == null || topK <= 0) ? DEFAULT_TOP_K : topK;
        List<VectorStore.ChunkHit> hits = kb.search(question, k);
        List<String> snippets = hits.stream().map(VectorStore.ChunkHit::content).toList();
        String answer = llm.generateFor("QA", question, snippets); // V49 场景路由：问答
        List<Citation> citations = hits.stream()
                .map(h -> new Citation(h.documentId(), h.seq(), snippet(h.content()), h.score()))
                .toList();
        appendAccessLog("AI_QA", question, hits.size());
        return new AiAnswer(answer, llm.name(), citations);
    }

    /** AI 访问留痕（八轮 8-2）：问题只留摘要（前 80 字），命中片段数与模型入链。留痕失败不阻断问答。 */
    void appendAccessLog(String action, String question, int hitCount) {
        try {
            var orgs = com.mandao.grc.common.isolation.IsolationContext.get();
            long orgId = orgs == null || orgs.isEmpty() ? 1L : orgs.get(0);
            String actor = com.mandao.grc.common.auth.ActorResolver.resolve(null);
            String q = question == null ? "" : (question.length() > 80 ? question.substring(0, 80) + "…" : question);
            hashChainService.append(orgId, action, actor, "AI:" + llm.name(),
                    "问题「" + q + "」命中知识片段 " + hitCount + " 段，模型=" + llm.model());
        } catch (RuntimeException e) {
            // 留痕失败不阻断问答（链异常单独告警排查）
        }
    }

    /** 当前 AI 提供方标识（前端展示用）。 */
    public String provider() {
        return llm.name();
    }

    /** 当前模型标识（前端展示用）。 */
    public String model() {
        return llm.model();
    }

    private String snippet(String content) {
        if (content == null) {
            return "";
        }
        return content.length() > SNIPPET_MAX ? content.substring(0, SNIPPET_MAX) + "…" : content;
    }
}
