package com.mandao.grc.modules.audit.management;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * 年度审计计划（V52 · A3）：风险导向的年度审计安排，一组织一年一份。
 * DRAFT 可增改对象清单，APPROVED 后冻结（对象转单项计划不受限）。
 */
@Entity
@Table(name = "audit_annual_plan")
public class AuditAnnualPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    @Column(nullable = false)
    private Integer year;

    @Column(nullable = false, length = 256)
    private String title;

    /** DRAFT / APPROVED。 */
    @Column(nullable = false, length = 12)
    private String status = "DRAFT";

    @Column(name = "approved_by", length = 64)
    private String approvedBy;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    protected AuditAnnualPlan() {
    }

    public AuditAnnualPlan(Long orgId, Integer year, String title, String createdBy) {
        this.orgId = orgId;
        this.year = year;
        this.title = title;
        this.createdBy = createdBy;
    }

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = OffsetDateTime.now();
        }
    }

    void approve(String approver) {
        this.status = "APPROVED";
        this.approvedBy = approver;
        this.approvedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public Long getOrgId() { return orgId; }
    public Integer getYear() { return year; }
    public String getTitle() { return title; }
    public String getStatus() { return status; }
    public String getApprovedBy() { return approvedBy; }
    public OffsetDateTime getApprovedAt() { return approvedAt; }
    public String getCreatedBy() { return createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
