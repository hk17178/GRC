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
 * 制度签署确认（M1）。
 *
 * 制度发布（PUBLISHED）后，相关人员逐一签署确认已阅知并承诺执行。
 * 表上 UNIQUE(policy_id, signer) 保证同一制度同一签署人只签一次（幂等/重复被拒）。
 * 携带 org_id 隔离锚点，与制度同口径受 RLS 裁剪。
 */
@Entity
@Table(name = "policy_signoff")
public class PolicySignoff {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属制度 id。 */
    @Column(name = "policy_id", nullable = false, updatable = false)
    private Long policyId;

    /** 隔离锚点：与所属制度一致（冗余存储，便于 RLS 与索引）。 */
    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    /** 签署人。 */
    @Column(nullable = false, length = 64)
    private String signer;

    @Column(name = "signed_at")
    private OffsetDateTime signedAt;

    /** JPA 要求的无参构造。 */
    protected PolicySignoff() {
    }

    /** 业务构造：记录一次签署确认。 */
    public PolicySignoff(Long policyId, Long orgId, String signer) {
        this.policyId = policyId;
        this.orgId = orgId;
        this.signer = signer;
    }

    /** 落库前补齐签署时间。 */
    @PrePersist
    void onCreate() {
        if (this.signedAt == null) {
            this.signedAt = OffsetDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Long getPolicyId() {
        return policyId;
    }

    public Long getOrgId() {
        return orgId;
    }

    public String getSigner() {
        return signer;
    }

    public OffsetDateTime getSignedAt() {
        return signedAt;
    }
}
