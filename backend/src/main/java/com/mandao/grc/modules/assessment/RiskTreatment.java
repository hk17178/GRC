package com.mandao.grc.modules.assessment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 风险处置计划条目（V51 · R3，ISO 27001 RTP）：一条风险发现一份处置计划。
 *
 * 结构化四要素：措施 / 责任人 / 期限 / 资源，另带预期残余等级与执行状态。
 * 与 risk_finding.treatment_decision（四选一决策）互补：决策定方向，RTP 定落地。
 */
@Entity
@Table(name = "risk_treatment")
public class RiskTreatment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    @Column(name = "finding_id", nullable = false, updatable = false)
    private Long findingId;

    /** 处置措施。 */
    @Column(columnDefinition = "TEXT")
    private String measure;

    /** 责任人。 */
    @Column(length = 64)
    private String owner;

    /** 完成期限。 */
    @Column(name = "due_date")
    private LocalDate dueDate;

    /** 所需资源/预算。 */
    @Column(columnDefinition = "TEXT")
    private String resource;

    /** 预期残余等级（五级）。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "expected_residual", length = 12)
    private RiskLevel expectedResidual;

    /** PENDING / IN_PROGRESS / DONE。 */
    @Column(nullable = false, length = 16)
    private String status = "PENDING";

    @Column(name = "updated_by", length = 64)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    protected RiskTreatment() {
    }

    public RiskTreatment(Long orgId, Long findingId) {
        this.orgId = orgId;
        this.findingId = findingId;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    /** 更新计划内容（由 Service 校验后调用）。 */
    public void apply(String measure, String owner, LocalDate dueDate, String resource,
                      RiskLevel expectedResidual, String status, String actor) {
        this.measure = measure;
        this.owner = owner;
        this.dueDate = dueDate;
        this.resource = resource;
        this.expectedResidual = expectedResidual;
        if (status != null) {
            this.status = status;
        }
        this.updatedBy = actor;
    }

    public Long getId() { return id; }
    public Long getOrgId() { return orgId; }
    public Long getFindingId() { return findingId; }
    public String getMeasure() { return measure; }
    public String getOwner() { return owner; }
    public LocalDate getDueDate() { return dueDate; }
    public String getResource() { return resource; }
    public RiskLevel getExpectedResidual() { return expectedResidual; }
    public String getStatus() { return status; }
    public String getUpdatedBy() { return updatedBy; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
