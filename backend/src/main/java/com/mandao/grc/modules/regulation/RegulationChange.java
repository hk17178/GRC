package com.mandao.grc.modules.regulation;

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

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 法规变更（法规动态）。
 *
 * 携带 org_id 隔离锚点（与所属法规同组织）；可见性/可写性由 RLS 自动裁剪。
 * 影响分析闭环：登记时 impactStatus=PENDING，经评估后置 ASSESSED 并记录受影响范围与处置（由 Service 校验流转）。
 */
@Entity
@Table(name = "regulation_change")
public class RegulationChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 隔离锚点：所属组织。 */
    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    /** 所属法规。 */
    @Column(name = "regulation_id", nullable = false, updatable = false)
    private Long regulationId;

    /** 变更类型。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false, length = 16)
    private ChangeType changeType;

    /** 变更日期。 */
    @Column(name = "change_date")
    private LocalDate changeDate;

    /** 变更说明。 */
    @Column(columnDefinition = "TEXT")
    private String description;

    /** 影响评估状态。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "impact_status", nullable = false, length = 16)
    private ImpactStatus impactStatus = ImpactStatus.PENDING;

    /** 受影响范围（评估时填写，如 涉及的制度/控制项/业务）。 */
    @Column(name = "impact_scope", columnDefinition = "TEXT")
    private String impactScope;

    /** 影响评估处置说明。 */
    @Column(name = "impact_note", columnDefinition = "TEXT")
    private String impactNote;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    /** JPA 要求的无参构造。 */
    protected RegulationChange() {
    }

    /** 业务构造：登记一条 PENDING 法规变更。 */
    public RegulationChange(Long orgId, Long regulationId, ChangeType changeType,
                            LocalDate changeDate, String description) {
        this.orgId = orgId;
        this.regulationId = regulationId;
        this.changeType = changeType;
        this.changeDate = changeDate;
        this.description = description;
        this.impactStatus = ImpactStatus.PENDING;
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

    /** 完成影响评估：记录受影响范围与处置，状态转 ASSESSED（由 Service 校验后调用）。 */
    void assess(String impactScope, String impactNote) {
        this.impactScope = impactScope;
        this.impactNote = impactNote;
        this.impactStatus = ImpactStatus.ASSESSED;
    }

    public Long getId() { return id; }
    public Long getOrgId() { return orgId; }
    public Long getRegulationId() { return regulationId; }
    public ChangeType getChangeType() { return changeType; }
    public LocalDate getChangeDate() { return changeDate; }
    public String getDescription() { return description; }
    public ImpactStatus getImpactStatus() { return impactStatus; }
    public String getImpactScope() { return impactScope; }
    public String getImpactNote() { return impactNote; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
