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
 * 年度审计计划条目（V52 · A3）：一个审计对象一行（风险排序 + 排期季度），
 * 转单项计划后回填 plan_id。
 */
@Entity
@Table(name = "audit_annual_item")
public class AuditAnnualItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    @Column(name = "annual_id", nullable = false, updatable = false)
    private Long annualId;

    @Column(nullable = false, length = 256)
    private String target;

    /** 风险排序 1（最高）–5。 */
    @Column(name = "risk_rank", nullable = false)
    private Integer riskRank = 3;

    /** 排期季度 Q1–Q4。 */
    @Column(nullable = false, length = 2)
    private String quarter = "Q1";

    @Column(columnDefinition = "TEXT")
    private String note;

    /** 生成的单项审计计划（软引用，可空）。 */
    @Column(name = "plan_id")
    private Long planId;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    protected AuditAnnualItem() {
    }

    public AuditAnnualItem(Long orgId, Long annualId, String target, Integer riskRank, String quarter, String note) {
        this.orgId = orgId;
        this.annualId = annualId;
        this.target = target;
        this.riskRank = riskRank == null ? 3 : riskRank;
        this.quarter = quarter == null ? "Q1" : quarter;
        this.note = note;
    }

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = OffsetDateTime.now();
        }
    }

    void linkPlan(Long planId) {
        this.planId = planId;
    }

    public Long getId() { return id; }
    public Long getOrgId() { return orgId; }
    public Long getAnnualId() { return annualId; }
    public String getTarget() { return target; }
    public Integer getRiskRank() { return riskRank; }
    public String getQuarter() { return quarter; }
    public String getNote() { return note; }
    public Long getPlanId() { return planId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
