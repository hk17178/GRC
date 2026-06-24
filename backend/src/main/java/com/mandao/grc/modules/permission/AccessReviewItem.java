package com.mandao.grc.modules.permission;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * UAR 逐项审阅决定（M8，org-scoped），映射 V7 access_review_item 表。
 *
 * 每项对应一条被审阅的授权四元组（{@link UserRoleOrg}）；decision 取值见 {@link AccessReviewDecision}。
 * decision=REVOKE 时由 {@link AccessReviewService#decideItem} 联动把对应 {@link UserRoleOrg} 置 active=false。
 *
 * 隔离锚点 org_id（USING/WITH CHECK，V7 已建）；主键 BIGSERIAL，用 {@link GenerationType#IDENTITY}。
 *
 * 设计依据：需求文档 M8 权限审批（UAR）、D1-3 §4.7、D2-5。
 */
@Entity
@Table(name = "access_review_item")
public class AccessReviewItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 隔离锚点：与所属审阅同组织。 */
    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    /** 所属审阅。 */
    @Column(name = "access_review_id", nullable = false, updatable = false)
    private Long accessReviewId;

    /** 被审阅的授权四元组。 */
    @Column(name = "user_role_org_id", nullable = false, updatable = false)
    private Long userRoleOrgId;

    /** 审阅决定（PENDING/KEEP/REVOKE）。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private AccessReviewDecision decision = AccessReviewDecision.PENDING;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    protected AccessReviewItem() {
    }

    public AccessReviewItem(Long orgId, Long accessReviewId, Long userRoleOrgId) {
        this.orgId = orgId;
        this.accessReviewId = accessReviewId;
        this.userRoleOrgId = userRoleOrgId;
        this.decision = AccessReviewDecision.PENDING;
    }

    public Long getId() { return id; }
    public Long getOrgId() { return orgId; }
    public Long getAccessReviewId() { return accessReviewId; }
    public Long getUserRoleOrgId() { return userRoleOrgId; }
    public AccessReviewDecision getDecision() { return decision; }
    public OffsetDateTime getReviewedAt() { return reviewedAt; }

    // 包级可见：仅由 Service 在校验后调用。
    void setDecision(AccessReviewDecision decision) { this.decision = decision; }
    void setReviewedAt(OffsetDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
}
