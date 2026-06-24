package com.mandao.grc.modules.assessment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * 风险接受（高残余风险关闭的审批凭据，CR-002 关闭门控的放行依据）。
 *
 * 携带 org_id 隔离锚点；可见性/可写性由 RLS 依据 app.visible_orgs 自动裁剪。
 * 主键为 BIGSERIAL 自增，故用 {@link GenerationType#IDENTITY}。
 */
@Entity
@Table(name = "risk_acceptance")
public class RiskAcceptance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 隔离锚点：所属组织。 */
    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    /** 所接受的风险发现。 */
    @Column(name = "finding_id", nullable = false, updatable = false)
    private Long findingId;

    /** 接受审批人。 */
    @Column(nullable = false, length = 64)
    private String approver;

    /** 接受理由。 */
    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name = "accepted_at", updatable = false)
    private OffsetDateTime acceptedAt;

    /** JPA 要求的无参构造。 */
    protected RiskAcceptance() {
    }

    /** 业务构造：登记一次风险接受。 */
    public RiskAcceptance(Long orgId, Long findingId, String approver, String reason) {
        this.orgId = orgId;
        this.findingId = findingId;
        this.approver = approver;
        this.reason = reason;
    }

    @PrePersist
    void onCreate() {
        if (this.acceptedAt == null) {
            this.acceptedAt = OffsetDateTime.now();
        }
    }

    public Long getId() { return id; }
    public Long getOrgId() { return orgId; }
    public Long getFindingId() { return findingId; }
    public String getApprover() { return approver; }
    public String getReason() { return reason; }
    public OffsetDateTime getAcceptedAt() { return acceptedAt; }
}
