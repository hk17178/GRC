package com.mandao.grc.modules.aml;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 可疑交易报告 STR（AML），映射 V80 str_report。
 *
 * 生命周期：DRAFT（登记）→ SUBMITTED（内部提交）→ REPORTED（已报送反洗钱监测中心）→ CLOSED（了结）。
 * 携 org_id 隔离锚点，RLS 裁剪；复用监管报送范式（状态机 + 留痕）。
 */
@Entity
@Table(name = "str_report")
public class StrReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    @Column(nullable = false, length = 128)
    private String subject;

    @Column
    private BigDecimal amount;

    @Column(name = "risk_level", nullable = false, length = 16)
    private String riskLevel = "MID";

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(nullable = false, length = 16)
    private String status = "DRAFT";

    @Column(name = "reported_to", length = 128)
    private String reportedTo;

    @Column(name = "report_no", length = 64)
    private String reportNo;

    @Column(name = "occurred_date")
    private LocalDate occurredDate;

    @Column(name = "reported_date")
    private LocalDate reportedDate;

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    protected StrReport() {
    }

    public StrReport(Long orgId, String subject, BigDecimal amount, String riskLevel,
                     String reason, LocalDate occurredDate, String createdBy) {
        this.orgId = orgId;
        this.subject = subject;
        this.amount = amount;
        this.riskLevel = riskLevel == null ? "MID" : riskLevel;
        this.reason = reason;
        this.occurredDate = occurredDate;
        this.createdBy = createdBy;
        this.status = "DRAFT";
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
    public String getSubject() { return subject; }
    public BigDecimal getAmount() { return amount; }
    public String getRiskLevel() { return riskLevel; }
    public String getReason() { return reason; }
    public String getStatus() { return status; }
    public String getReportedTo() { return reportedTo; }
    public String getReportNo() { return reportNo; }
    public LocalDate getOccurredDate() { return occurredDate; }
    public LocalDate getReportedDate() { return reportedDate; }
    public String getCreatedBy() { return createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    void setStatus(String status) { this.status = status; }
    void report(String reportedTo, String reportNo, LocalDate reportedDate) {
        this.reportedTo = reportedTo;
        this.reportNo = reportNo;
        this.reportedDate = reportedDate;
    }
}
