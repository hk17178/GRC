package com.mandao.grc.modules.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * 知识源文档（AI 知识库 / RAG · M_AI）。
 *
 * 携 org_id 隔离锚点；可见性/可写性由 RLS 依据 app.visible_orgs 自动裁剪。
 *
 * 登记后状态 PENDING；经 {@link KnowledgeBaseService} 切块 + 嵌入后置 INDEXED 并回填 chunkCount。
 * 切块向量存于 {@link KbChunk}（embedding 经原生 SQL 读写）。
 */
@Entity
@Table(name = "kb_document")
public class KbDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 隔离锚点：所属组织。 */
    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    /** 文档标题。 */
    @Column(nullable = false, length = 256)
    private String title;

    /** 来源类型。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 32)
    private KbSourceType sourceType;

    /** 来源引用（如制度编号/法规号；可空）。 */
    @Column(name = "source_ref", length = 128)
    private String sourceRef;

    /** 原文（切块前留存）。 */
    @Column(columnDefinition = "TEXT")
    private String content;

    /** 索引状态。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private KbDocStatus status = KbDocStatus.PENDING;

    /** 已生成切块数。 */
    @Column(name = "chunk_count", nullable = false)
    private Integer chunkCount = 0;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    /** JPA 要求的无参构造。 */
    protected KbDocument() {
    }

    /** 业务构造：以 PENDING 态登记一篇知识源文档。 */
    public KbDocument(Long orgId, String title, KbSourceType sourceType, String sourceRef, String content) {
        this.orgId = orgId;
        this.title = title;
        this.sourceType = sourceType;
        this.sourceRef = sourceRef;
        this.content = content;
        this.status = KbDocStatus.PENDING;
        this.chunkCount = 0;
    }

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    /** 由 Service 在切块嵌入完成后回写：置 INDEXED 并记录块数。 */
    void markIndexed(int chunkCount) {
        this.status = KbDocStatus.INDEXED;
        this.chunkCount = chunkCount;
    }

    public Long getId() { return id; }
    public Long getOrgId() { return orgId; }
    public String getTitle() { return title; }
    public KbSourceType getSourceType() { return sourceType; }
    public String getSourceRef() { return sourceRef; }
    public String getContent() { return content; }
    public KbDocStatus getStatus() { return status; }
    public Integer getChunkCount() { return chunkCount; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
