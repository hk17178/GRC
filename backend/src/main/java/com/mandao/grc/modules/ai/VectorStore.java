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

    /** A16：过取倍数——先取 topK×此倍数的候选，再重排/去重/滤噪收敛到 topK。 */
    private static final int OVERFETCH = 4;
    /** A16：单文档最多保留的切块数（防一篇长文档霸榜，牺牲引用多样性）。 */
    private static final int MAX_PER_DOC = 2;
    /** A16：相对阈值——低于「最高分×此比例」的候选视为弱相关丢弃（0~1）。 */
    private static final double REL_THRESHOLD = 0.55;

    /**
     * 余弦相似召回 top-k（仅在可见组织范围内，RLS 兜底）。
     * 返回按相似度降序的命中列表。embedding 为空的块（尚未嵌入）被排除。
     *
     * A16 检索排序优化：不再直接取库内 top-k，而是过取候选后重排——
     *  1) 相对阈值：丢弃分数低于「最高分×{@link #REL_THRESHOLD}」的弱相关块（滤噪）；
     *  2) 每文档限额：同一文档至多 {@link #MAX_PER_DOC} 块，提升引用来源多样性；
     *  3) 收敛到 topK。库内已按距离升序返回，故遍历顺序即相关度降序。
     */
    @SuppressWarnings("unchecked")
    public List<ChunkHit> search(float[] query, int topK) {
        int fetch = Math.max(topK * OVERFETCH, topK);
        List<Object[]> rows = em.createNativeQuery(
                        "SELECT id, document_id, seq, content, " +
                                "       1 - (embedding <=> CAST(:q AS vector)) AS score " +
                                "FROM kb_chunk " +
                                "WHERE embedding IS NOT NULL " +
                                "ORDER BY embedding <=> CAST(:q AS vector) " +
                                "LIMIT :k")
                .setParameter("q", toLiteral(query))
                .setParameter("k", fetch)
                .getResultList();
        if (rows.isEmpty()) {
            return List.of();
        }
        double best = ((Number) rows.get(0)[4]).doubleValue();
        // 相对阈值仅对正相似度有意义：best<=0 时（弱相关整体偏低）不滤，避免把最高分也误丢导致零引用
        double floor = best > 0 ? best * REL_THRESHOLD : Double.NEGATIVE_INFINITY;
        java.util.Map<Long, Integer> perDoc = new java.util.HashMap<>();
        List<ChunkHit> hits = new ArrayList<>(topK);
        for (Object[] r : rows) {
            if (hits.size() >= topK) {
                break;
            }
            double score = ((Number) r[4]).doubleValue();
            if (score < floor) {
                break; // 已按分数降序，后续只会更低
            }
            Long docId = ((Number) r[1]).longValue();
            int used = perDoc.getOrDefault(docId, 0);
            if (used >= MAX_PER_DOC) {
                continue; // 该文档已达限额，跳过但继续看后面别的文档
            }
            perDoc.put(docId, used + 1);
            hits.add(new ChunkHit(
                    ((Number) r[0]).longValue(),
                    docId,
                    ((Number) r[2]).intValue(),
                    (String) r[3],
                    score));
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
