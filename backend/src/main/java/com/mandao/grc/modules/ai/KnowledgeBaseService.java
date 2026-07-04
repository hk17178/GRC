package com.mandao.grc.modules.ai;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 知识库服务（RAG 摄入与召回）。
 *
 * 摄入：登记文档 → 切块 → 逐块嵌入并存向量 → 文档置 INDEXED。
 * 召回：对查询嵌入后在 pgvector 上做余弦 top-k 近邻检索。
 *
 * 隔离：方法 @Transactional 且位于 com.mandao.grc.modules 包内 → {@link com.mandao.grc.common.isolation.OrgScopeAspect}
 * 在事务内注入 app.visible_orgs，RLS 自动裁剪文档/切块；本服务不手写 org 过滤。
 */
@Service
public class KnowledgeBaseService {

    /** 单块最大字符数（超过则按定长再切；中文按字计）。 */
    private static final int MAX_CHARS = 300;

    private final KbDocumentRepository docRepo;
    private final KbChunkRepository chunkRepo;
    private final EmbeddingProvider embedding;
    private final VectorStore vectorStore;

    public KnowledgeBaseService(KbDocumentRepository docRepo, KbChunkRepository chunkRepo,
                                EmbeddingProvider embedding, VectorStore vectorStore) {
        this.docRepo = docRepo;
        this.chunkRepo = chunkRepo;
        this.embedding = embedding;
        this.vectorStore = vectorStore;
    }

    /** 摄入一篇文档：登记 → 切块 → 嵌入入库 → 置 INDEXED，返回文档。 */
    @Transactional
    public KbDocument ingest(Long orgId, String title, KbSourceType type, String sourceRef, String content) {
        KbDocument doc = docRepo.save(new KbDocument(orgId, title, type, sourceRef, content));
        List<String> pieces = chunk(content);
        int seq = 1;
        for (String piece : pieces) {
            KbChunk ch = chunkRepo.save(new KbChunk(orgId, doc.getId(), seq++, piece));
            vectorStore.saveEmbedding(ch.getId(), embedding.embed(piece));
        }
        doc.markIndexed(pieces.size());
        return docRepo.save(doc);
    }

    /** 列出可见文档（最新在前）。 */
    @Transactional(readOnly = true)
    public List<KbDocument> list() {
        return docRepo.findAllByOrderByIdDesc();
    }

    /** 列出某文档的切块。 */
    @Transactional(readOnly = true)
    public List<KbChunk> chunks(Long documentId) {
        return chunkRepo.findByDocumentIdOrderBySeqAsc(documentId);
    }

    /** 对查询做向量召回 top-k（可见范围内）。 */
    @Transactional(readOnly = true)
    public List<VectorStore.ChunkHit> search(String query, int topK) {
        return vectorStore.search(embedding.embed(query), topK);
    }

    /** 删除文档及其全部切块（连同向量）；仅能删可见组织内的文档（RLS 裁剪）。 */
    @Transactional
    public void delete(Long documentId) {
        KbDocument doc = docRepo.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("知识源文档不存在或不可见：id=" + documentId));
        chunkRepo.deleteAll(chunkRepo.findByDocumentIdOrderBySeqAsc(documentId));
        docRepo.delete(doc);
    }

    /**
     * 复摄入（八轮 8-4：制度生效/修订自动入索引的幂等入口）：
     * 同 (type, sourceRef) 已有文档则删旧重建（内容/切块/向量全量替换），否则新摄入。
     */
    @Transactional
    public KbDocument upsertBySource(Long orgId, String title, KbSourceType type, String sourceRef, String content) {
        docRepo.findFirstBySourceTypeAndSourceRef(type, sourceRef).ifPresent(old -> {
            chunkRepo.deleteAll(chunkRepo.findByDocumentIdOrderBySeqAsc(old.getId()));
            docRepo.delete(old);
        });
        return ingest(orgId, title, type, sourceRef, content);
    }

    /**
     * 条款边界切块（八轮 8-4 / 评估报告 B9，M1-9 红线级）：
     * 制度/法规文本按「第X章 / 第X条 / 数字标题」等条款边界切，切块首行保留条款号——
     * RAG 召回后引用能落到条款粒度，而不是无语义的定长碎片。
     * 规则：
     *  1) 先按条款边界正则切出「条」级片段（第X条 优先，兼容 一、二、… 与 1. 2. 编号）；
     *  2) 无条款结构的文本回退到 空行分段 + 定长兜底；
     *  3) 过长条款仍按 MAX_CHARS 续切，但每段都携带条款号前缀（保住出处）。
     */
    public static List<String> chunk(String content) {
        List<String> out = new ArrayList<>();
        if (content == null) {
            return out;
        }
        String text = content.strip();
        if (text.isEmpty()) {
            return out;
        }
        // 条款边界：行首的 第X条/第X章/第X节，或 一、/（一）/1. 式编号
        java.util.regex.Pattern boundary = java.util.regex.Pattern.compile(
                "(?m)^(?=\\s*(第[一二三四五六七八九十百零\\d]+[条章节]|[一二三四五六七八九十]+、|（[一二三四五六七八九十]+）|\\d{1,2}[.、]\\s))");
        String[] clauses = boundary.split(text);
        boolean hasClauseStructure = clauses.length >= 3; // 至少切出两个条款边界才算有结构
        if (hasClauseStructure) {
            for (String clause : clauses) {
                String c = clause.strip();
                if (c.isEmpty()) {
                    continue;
                }
                if (c.length() <= MAX_CHARS) {
                    out.add(c);
                } else {
                    // 条款过长续切：每段带条款号前缀（取首行前 24 字）保出处
                    String head = c.lines().findFirst().orElse("");
                    String prefix = (head.length() > 24 ? head.substring(0, 24) : head) + "（续）";
                    out.add(c.substring(0, MAX_CHARS));
                    for (int i = MAX_CHARS; i < c.length(); i += MAX_CHARS - prefix.length()) {
                        out.add(prefix + c.substring(i, Math.min(c.length(), i + MAX_CHARS - prefix.length())));
                    }
                }
            }
            if (!out.isEmpty()) {
                return out;
            }
        }
        // 回退：空行分段 + 定长兜底（无条款结构的说明性文本）
        for (String para : text.split("\\n\\s*\\n")) {
            String p = para.strip();
            if (p.isEmpty()) {
                continue;
            }
            if (p.length() <= MAX_CHARS) {
                out.add(p);
            } else {
                for (int i = 0; i < p.length(); i += MAX_CHARS) {
                    out.add(p.substring(i, Math.min(p.length(), i + MAX_CHARS)));
                }
            }
        }
        if (out.isEmpty()) {
            out.add(text);
        }
        return out;
    }
}
