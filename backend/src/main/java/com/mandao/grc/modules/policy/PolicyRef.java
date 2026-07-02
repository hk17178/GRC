package com.mandao.grc.modules.policy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * 制度间引用关系（需求 3.2.1：制度文件之间引用关系标注）。
 *
 * policy_id 引用 ref_policy_id（如《实施细则》引用《管理办法》）。携 org_id，RLS 裁剪。
 */
@Entity
@Table(name = "policy_ref")
public class PolicyRef {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    @Column(name = "policy_id", nullable = false)
    private Long policyId;

    @Column(name = "ref_policy_id", nullable = false)
    private Long refPolicyId;

    @Column(length = 256)
    private String note;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    protected PolicyRef() {
    }

    public PolicyRef(Long orgId, Long policyId, Long refPolicyId, String note) {
        this.orgId = orgId;
        this.policyId = policyId;
        this.refPolicyId = refPolicyId;
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
    public Long getPolicyId() { return policyId; }
    public Long getRefPolicyId() { return refPolicyId; }
    public String getNote() { return note; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
