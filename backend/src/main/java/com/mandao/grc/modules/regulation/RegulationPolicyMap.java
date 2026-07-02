package com.mandao.grc.modules.regulation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * 法规-制度映射（需求 6.2：法规条款命中制度，支撑"法规-制度映射概览"与变更核查）。
 * 携 org_id，RLS 裁剪；(regulation, policy, clause) 唯一防重。
 */
@Entity
@Table(name = "regulation_policy_map")
public class RegulationPolicyMap {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    @Column(name = "regulation_id", nullable = false)
    private Long regulationId;

    @Column(name = "policy_id", nullable = false)
    private Long policyId;

    /** 法规条款（如 §41）。 */
    @Column(length = 128)
    private String clause;

    /** 映射说明。 */
    @Column(length = 256)
    private String note;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    protected RegulationPolicyMap() {
    }

    public RegulationPolicyMap(Long orgId, Long regulationId, Long policyId, String clause, String note) {
        this.orgId = orgId;
        this.regulationId = regulationId;
        this.policyId = policyId;
        this.clause = clause;
        this.note = note;
    }

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = OffsetDateTime.now();
        }
    }

    public Long getId() { return id; }
    public Long getOrgId() { return orgId; }
    public Long getRegulationId() { return regulationId; }
    public Long getPolicyId() { return policyId; }
    public String getClause() { return clause; }
    public String getNote() { return note; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
