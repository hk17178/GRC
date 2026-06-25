package com.mandao.grc.modules.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * 文档切块（AI 知识库 / RAG）。
 *
 * 携 org_id 隔离锚点；可见性/可写性由 RLS 依据 app.visible_orgs 自动裁剪。
 *
 * 注意：向量列 embedding（pgvector vector(1024)）<b>不</b>映射为 JPA 字段——Hibernate 无原生 vector
 * 类型，嵌入向量由 {@link VectorStore} 经原生 SQL 回写与检索（仿 operation_log 哈希链的原生 SQL 做法）。
 * 本实体仅承载块的元数据与文本，便于按文档列出切块。
 */
@Entity
@Table(name = "kb_chunk")
public class KbChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 隔离锚点：所属组织（与所属文档同 org）。 */
    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    /** 所属文档。 */
    @Column(name = "document_id", nullable = false, updatable = false)
    private Long documentId;

    /** 块序号（文档内从 1 起）。 */
    @Column(nullable = false)
    private Integer seq;

    /** 块文本。 */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    /** JPA 要求的无参构造。 */
    protected KbChunk() {
    }

    /** 业务构造：新建一个文档切块（embedding 由 VectorStore 随后原生回写）。 */
    public KbChunk(Long orgId, Long documentId, Integer seq, String content) {
        this.orgId = orgId;
        this.documentId = documentId;
        this.seq = seq;
        this.content = content;
    }

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = OffsetDateTime.now();
        }
    }

    public Long getId() { return id; }
    public Long getOrgId() { return orgId; }
    public Long getDocumentId() { return documentId; }
    public Integer getSeq() { return seq; }
    public String getContent() { return content; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
