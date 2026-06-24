package com.mandao.grc.modules.assessment;

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
 * 风险发现（M2 风险评估的核心子实体）。
 *
 * 携带 org_id 隔离锚点；可见性/可写性由 RLS 依据 app.visible_orgs 自动裁剪。
 * 主键为 BIGSERIAL 自增，故用 {@link GenerationType#IDENTITY}。
 *
 * 关闭门控（CR-002 红线）：residual_level ∈ {HIGH, VERY_HIGH} 时，
 * 关闭（DONE/VERIFIED）需先具备有效 risk_acceptance（由 {@link RiskFindingService} 校验）。
 * riskAcceptanceId 即放行凭据的回填字段，由 accept() 写入。
 */
@Entity
@Table(name = "risk_finding")
public class RiskFinding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 隔离锚点：所属组织。 */
    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    /** 所属评估。 */
    @Column(name = "assessment_id", nullable = false, updatable = false)
    private Long assessmentId;

    /** 风险描述。 */
    @Column(length = 256)
    private String title;

    /** 固有风险等级（五级）。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "inherent_level", length = 12)
    private RiskLevel inherentLevel;

    /** 处置方案。 */
    @Column(name = "treatment_plan", columnDefinition = "TEXT")
    private String treatmentPlan;

    /** 残余风险等级（五级，关闭门控判定依据）。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "residual_level", length = 12)
    private RiskLevel residualLevel;

    /** 回填：有效风险接受凭据 id（高残余关闭的放行凭据）。 */
    @Column(name = "risk_acceptance_id")
    private Long riskAcceptanceId;

    /** 状态机当前态。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RiskFindingStatus status = RiskFindingStatus.OPEN;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    /** JPA 要求的无参构造。 */
    protected RiskFinding() {
    }

    /** 业务构造：以 OPEN 态新建一条风险发现。 */
    public RiskFinding(Long orgId, Long assessmentId, String title, RiskLevel inherentLevel) {
        this.orgId = orgId;
        this.assessmentId = assessmentId;
        this.title = title;
        this.inherentLevel = inherentLevel;
        this.status = RiskFindingStatus.OPEN;
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
    public Long getAssessmentId() { return assessmentId; }
    public String getTitle() { return title; }
    public RiskLevel getInherentLevel() { return inherentLevel; }
    public String getTreatmentPlan() { return treatmentPlan; }
    public RiskLevel getResidualLevel() { return residualLevel; }
    public Long getRiskAcceptanceId() { return riskAcceptanceId; }
    public RiskFindingStatus getStatus() { return status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    // 以下 setter 为包级可见，仅由 Service 在校验后调用，封装状态变更。
    void setTreatmentPlan(String treatmentPlan) { this.treatmentPlan = treatmentPlan; }
    void setResidualLevel(RiskLevel residualLevel) { this.residualLevel = residualLevel; }
    void setRiskAcceptanceId(Long riskAcceptanceId) { this.riskAcceptanceId = riskAcceptanceId; }
    void setStatus(RiskFindingStatus status) { this.status = status; }
}
