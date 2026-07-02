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
     * 简易切块：先按空行分段，过长段再按 {@link #MAX_CHARS} 定长切。
     * 足以驱动 RAG；后续可换更讲究的语义切块（保留中文句界等）。
     */
    static List<String> chunk(String content) {
        List<String> out = new ArrayList<>();
        if (content == null) {
            return out;
        }
        String text = content.strip();
        if (text.isEmpty()) {
            return out;
        }
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
