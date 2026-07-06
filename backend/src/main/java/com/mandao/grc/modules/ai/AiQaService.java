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

    /** 反馈摘要最大留存字符（问/答只留摘要，避免长文本与隐私堆积）。 */
    private static final int FEEDBACK_MAX = 500;

    private final KnowledgeBaseService kb;
    private final LlmProvider llm;

    /** 留痕链（八轮 8-2：AI 访问日志，setter 注入）。 */
    private com.mandao.grc.modules.audit.HashChainService hashChainService;

    @org.springframework.beans.factory.annotation.Autowired
    void wireHashChain(com.mandao.grc.modules.audit.HashChainService hashChainService) {
        this.hashChainService = hashChainService;
    }

    /** 反馈仓储（B32，setter 注入避免构造膨胀）。 */
    private AiFeedbackRepository feedbackRepo;

    @org.springframework.beans.factory.annotation.Autowired
    void wireFeedbackRepo(AiFeedbackRepository feedbackRepo) {
        this.feedbackRepo = feedbackRepo;
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

    /** 一轮历史对话（B26 多轮：前端回传近几轮，供 LLM 理解追问上下文）。 */
    public record Turn(String question, String answer) {
    }

    /** B26：携带上下文的历史轮数上限（超出只取最近的，控制 prompt 体量）。 */
    private static final int MAX_HISTORY_TURNS = 6;

    /** 兼容重载：无历史的单轮提问。 */
    public AiAnswer ask(String question, Integer topK) {
        return ask(question, topK, null);
    }

    /**
     * 提问：召回 → 生成 → 带引用返回；访问留痕入链（M5-5 事后审计依据）。
     *
     * B26 多轮对话：检索仍以当前问题为准（保证召回聚焦），但送入 LLM 的问题会前置最近几轮
     * 「问/答」历史，使「那它的处罚呢？」这类指代性追问能被正确理解。历史仅影响生成，不影响召回。
     */
    public AiAnswer ask(String question, Integer topK, List<Turn> history) {
        int k = (topK == null || topK <= 0) ? DEFAULT_TOP_K : topK;
        List<VectorStore.ChunkHit> hits = kb.search(question, k);
        List<String> snippets = hits.stream().map(VectorStore.ChunkHit::content).toList();
        String llmQuestion = withHistory(question, history); // B26：拼历史上下文
        String answer = llm.generateFor("QA", llmQuestion, snippets); // V49 场景路由：问答
        List<Citation> citations = hits.stream()
                .map(h -> new Citation(h.documentId(), h.seq(), snippet(h.content()), h.score()))
                .toList();
        appendAccessLog("AI_QA", question, hits.size());
        return new AiAnswer(answer, llm.name(), citations);
    }

    /** B26：把最近几轮对话拼为「对话历史」前言 + 当前问题；无历史时原样返回。 */
    private String withHistory(String question, List<Turn> history) {
        if (history == null || history.isEmpty()) {
            return question;
        }
        int from = Math.max(0, history.size() - MAX_HISTORY_TURNS);
        StringBuilder sb = new StringBuilder("【对话历史】\n");
        for (int i = from; i < history.size(); i++) {
            Turn t = history.get(i);
            if (t == null || t.question() == null) {
                continue;
            }
            sb.append("问：").append(t.question().trim()).append('\n');
            if (t.answer() != null && !t.answer().isBlank()) {
                sb.append("答：").append(t.answer().trim()).append('\n');
            }
        }
        sb.append("\n【当前问题】\n").append(question);
        return sb.toString();
    }

    /**
     * B32：记录 AI 回答反馈（赞/踩 + 可选原因）。@Transactional 触发 OrgScopeAspect 注入 visible_orgs，
     * RLS 写校验保证只能落到可见组织；问/答只留摘要。踩（helpful=false）额外入哈希链，便于质量追溯。
     */
    @org.springframework.transaction.annotation.Transactional
    public void recordFeedback(String question, String answer, boolean helpful, String reason, String actor) {
        var orgs = com.mandao.grc.common.isolation.IsolationContext.get();
        long orgId = orgs == null || orgs.isEmpty() ? 1L : orgs.get(0);
        feedbackRepo.save(new AiFeedback(orgId, cap(question), cap(answer), helpful,
                reason == null ? null : cap(reason), actor));
        if (!helpful) {
            appendAccessLog("AI_FEEDBACK_NEG", question, 0);
        }
    }

    /** 摘要截断（问/答/原因入库前）。 */
    private String cap(String s) {
        if (s == null) {
            return null;
        }
        return s.length() > FEEDBACK_MAX ? s.substring(0, FEEDBACK_MAX) + "…" : s;
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
