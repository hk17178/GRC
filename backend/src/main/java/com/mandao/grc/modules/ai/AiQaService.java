package com.mandao.grc.modules.ai;

import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 检索增强问答服务（RAG）。
 *
 * 流程：对问题做向量召回 top-k 知识片段 → 交由 {@link LlmProvider} 依据片段生成回答 → 连同引用一并返回。
 * 隔离：召回经 {@link KnowledgeBaseService#search}（@Transactional + RLS），只命中可见组织的知识。
 */
@Service
public class AiQaService {

    /** 召回片段摘要最大字符数（用于引用展示）。 */
    private static final int SNIPPET_MAX = 120;
    /** 默认召回条数。 */
    private static final int DEFAULT_TOP_K = 4;

    private final KnowledgeBaseService kb;
    private final LlmProvider llm;

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

    /** 提问：召回 → 生成 → 带引用返回。 */
    public AiAnswer ask(String question, Integer topK) {
        int k = (topK == null || topK <= 0) ? DEFAULT_TOP_K : topK;
        List<VectorStore.ChunkHit> hits = kb.search(question, k);
        List<String> snippets = hits.stream().map(VectorStore.ChunkHit::content).toList();
        String answer = llm.generate(question, snippets);
        List<Citation> citations = hits.stream()
                .map(h -> new Citation(h.documentId(), h.seq(), snippet(h.content()), h.score()))
                .toList();
        return new AiAnswer(answer, llm.name(), citations);
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
