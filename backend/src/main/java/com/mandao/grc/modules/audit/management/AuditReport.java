package com.mandao.grc.modules.audit.management;

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
 * 审计报告（V47 · A1）。一计划一报告（uk_audit_report_plan）。
 *
 * 生命周期：DRAFT 草稿 → COMMENTING 征求意见 → FINAL 定稿 → ISSUED 签发（终态）。
 * 草稿由 {@link AuditReportService#createDraft} 按 计划+发现五要素+整改台账 自动组稿，
 * 定稿前正文/意见可编辑；签发落 issued_by/issued_at 并写哈希链。
 */
@Entity
@Table(name = "audit_report")
public class AuditReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    @Column(name = "plan_id", nullable = false, updatable = false)
    private Long planId;

    @Column(nullable = false, length = 256)
    private String title;

    /** 审计意见（四级，定稿前可空）。 */
    @Enumerated(EnumType.STRING)
    @Column(length = 28)
    private AuditOpinion opinion;

    /** 审计概述与总体评价。 */
    @Column(columnDefinition = "TEXT")
    private String summary;

    /** 报告正文（自动组稿后可编辑）。 */
    @Column(columnDefinition = "TEXT")
    private String content;

    /** DRAFT / COMMENTING / FINAL / ISSUED。 */
    @Column(nullable = false, length = 16)
    private String status = "DRAFT";

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @Column(name = "issued_by", length = 64)
    private String issuedBy;

    @Column(name = "issued_at")
    private OffsetDateTime issuedAt;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    protected AuditReport() {
    }

    public AuditReport(Long orgId, Long planId, String title, String summary, String content, String createdBy) {
        this.orgId = orgId;
        this.planId = planId;
        this.title = title;
        this.summary = summary;
        this.content = content;
        this.createdBy = createdBy;
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

    /** 编辑报告（由 Service 校验非终态后调用）。 */
    void applyEdit(String title, AuditOpinion opinion, String summary, String content) {
        if (title != null && !title.isBlank()) {
            this.title = title;
        }
        this.opinion = opinion;
        this.summary = summary;
        this.content = content;
    }

    void setStatus(String status) {
        this.status = status;
    }

    void issue(String issuer) {
        this.status = "ISSUED";
        this.issuedBy = issuer;
        this.issuedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public Long getOrgId() { return orgId; }
    public Long getPlanId() { return planId; }
    public String getTitle() { return title; }
    public AuditOpinion getOpinion() { return opinion; }
    public String getSummary() { return summary; }
    public String getContent() { return content; }
    public String getStatus() { return status; }
    public String getCreatedBy() { return createdBy; }
    public String getIssuedBy() { return issuedBy; }
    public OffsetDateTime getIssuedAt() { return issuedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
