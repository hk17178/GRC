package com.mandao.grc.modules.regulatory;

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
 * 年度合规计划（M11 监管事项·年度计划）。
 *
 * 携带 org_id 隔离锚点；可见性/可写性由 RLS 依据 app.visible_orgs 自动裁剪。
 * 由若干 {@link CompliancePlanItem} 组成年度合规工作项；状态机 DRAFT → ACTIVE → CLOSED。
 */
@Entity
@Table(name = "compliance_plan")
public class CompliancePlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 隔离锚点：所属组织。 */
    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    /** 计划年度（如 2026）。 */
    @Column(nullable = false)
    private Integer year;

    /** 计划名称。 */
    @Column(nullable = false, length = 128)
    private String title;

    /** 状态机当前态。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CompliancePlanStatus status = CompliancePlanStatus.DRAFT;

    /** 责任人（可空）。 */
    @Column(length = 64)
    private String owner;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    /** JPA 要求的无参构造。 */
    protected CompliancePlan() {
    }

    /** 业务构造：以 DRAFT 态新建年度计划。 */
    public CompliancePlan(Long orgId, Integer year, String title, String owner) {
        this.orgId = orgId;
        this.year = year;
        this.title = title;
        this.owner = owner;
        this.status = CompliancePlanStatus.DRAFT;
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

    /** 由 Service 在校验后推进状态机。 */
    void setStatus(CompliancePlanStatus status) {
        this.status = status;
    }

    public Long getId() { return id; }
    public Long getOrgId() { return orgId; }
    public Integer getYear() { return year; }
    public String getTitle() { return title; }
    public CompliancePlanStatus getStatus() { return status; }
    public String getOwner() { return owner; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
