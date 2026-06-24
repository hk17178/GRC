package com.mandao.grc.modules.permission;

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
 * UAR 权限审阅主实体（M8，org-scoped），映射 V7 access_review 表。
 *
 * 状态机：OPEN → IN_REVIEW → COMPLETED（见 {@link AccessReviewService}）。
 * 逐项决定承载于 {@link AccessReviewItem}。
 *
 * 隔离锚点 org_id（USING/WITH CHECK，V7 已建）；主键 BIGSERIAL，用 {@link GenerationType#IDENTITY}。
 *
 * 设计依据：需求文档 M8 权限审批（UAR）、D1-3 §4.7、D2-5。
 */
@Entity
@Table(name = "access_review")
public class AccessReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 隔离锚点：审阅所在组织。 */
    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    /** 审阅周期（如 2026Q2）。 */
    @Column(length = 16)
    private String period;

    /** 状态机当前态。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AccessReviewStatus status = AccessReviewStatus.OPEN;

    /** 审阅人。 */
    @Column(length = 64)
    private String reviewer;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    protected AccessReview() {
    }

    public AccessReview(Long orgId, String period, String reviewer) {
        this.orgId = orgId;
        this.period = period;
        this.reviewer = reviewer;
        this.status = AccessReviewStatus.OPEN;
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

    public Long getId() { return id; }
    public Long getOrgId() { return orgId; }
    public String getPeriod() { return period; }
    public AccessReviewStatus getStatus() { return status; }
    public String getReviewer() { return reviewer; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    // 包级可见：仅由 Service 在校验后调用。
    void setStatus(AccessReviewStatus status) { this.status = status; }
}
