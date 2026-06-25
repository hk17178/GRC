package com.mandao.grc.modules.ai;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 向量存取（pgvector，经原生 SQL）。
 *
 * Hibernate 无原生 vector 类型，故 embedding 列的回写与相似检索都走原生 SQL（仿哈希链做法）。
 * 隔离：kb_chunk 挂 RLS，由 {@link com.mandao.grc.common.isolation.OrgScopeAspect} 在事务内注入
 * app.visible_orgs 自动裁剪——本类不手写 org 过滤，调用方须在带隔离上下文的事务中使用。
 *
 * 距离算子用余弦距离 {@code <=>}（配合 V24 的 hnsw vector_cosine_ops 索引）；相似度 = 1 - 距离。
 */
@Component
public class VectorStore {

    @PersistenceContext
    private EntityManager em;

    /** 单个切块的命中结果。 */
    public record ChunkHit(Long chunkId, Long documentId, Integer seq, String content, double score) {
    }

    /** 回写某切块的嵌入向量（CAST 文本字面量为 vector，规避 Hibernate 对 :: 的误解析）。 */
    public void saveEmbedding(Long chunkId, float[] embedding) {
        em.createNativeQuery("UPDATE kb_chunk SET embedding = CAST(:vec AS vector) WHERE id = :id")
                .setParameter("vec", toLiteral(embedding))
                .setParameter("id", chunkId)
                .executeUpdate();
    }

    /**
     * 余弦相似召回 top-k（仅在可见组织范围内，RLS 兜底）。
     * 返回按相似度降序的命中列表。embedding 为空的块（尚未嵌入）被排除。
     */
    @SuppressWarnings("unchecked")
    public List<ChunkHit> search(float[] query, int topK) {
        List<Object[]> rows = em.createNativeQuery(
                        "SELECT id, document_id, seq, content, " +
                                "       1 - (embedding <=> CAST(:q AS vector)) AS score " +
                                "FROM kb_chunk " +
                                "WHERE embedding IS NOT NULL " +
                                "ORDER BY embedding <=> CAST(:q AS vector) " +
                                "LIMIT :k")
                .setParameter("q", toLiteral(query))
                .setParameter("k", topK)
                .getResultList();
        List<ChunkHit> hits = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            hits.add(new ChunkHit(
                    ((Number) r[0]).longValue(),
                    ((Number) r[1]).longValue(),
                    ((Number) r[2]).intValue(),
                    (String) r[3],
                    ((Number) r[4]).doubleValue()));
        }
        return hits;
    }

    /** float[] → pgvector 文本字面量 "[v0,v1,...]"（ROOT 区域，避免逗号小数点本地化）。 */
    private String toLiteral(float[] v) {
        StringBuilder sb = new StringBuilder(v.length * 8 + 2);
        sb.append('[');
        for (int i = 0; i < v.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(String.format(Locale.ROOT, "%.6f", v[i]));
        }
        sb.append(']');
        return sb.toString();
    }
}
