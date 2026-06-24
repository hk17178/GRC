package com.mandao.grc.modules.assessment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * 风险接受（高残余风险关闭的放行凭据，CR-002 关闭门控的放行依据）。
 *
 * 携带 org_id 隔离锚点；可见性/可写性由 RLS 依据 app.visible_orgs 自动裁剪。
 * 主键为 BIGSERIAL 自增，故用 {@link GenerationType#IDENTITY}。
 *
 * ===== 审批化（A2）=====
 * 改为「申请→审批」两段式：申请时以 {@link AcceptanceStatus#PENDING} 登记（不放行）；
 * 经 Flowable 审批通过 {@link #approve} 后才置 APPROVED 并由 Service 回填 finding.risk_acceptance_id
 * （门控解除）；驳回 {@link #reject} 置 REJECTED、不放行。即高残余关闭须经【审批通过】的风险接受。
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

    /** 申请人（提交风险接受申请者）。 */
    @Column(length = 64)
    private String requester;

    /** 接受理由。 */
    @Column(columnDefinition = "TEXT")
    private String reason;

    /** 审批状态（PENDING/APPROVED/REJECTED）。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AcceptanceStatus status = AcceptanceStatus.PENDING;

    /** 审批人（审批通过/驳回时落定，PENDING 时为空）。 */
    @Column(length = 64)
    private String approver;

    /** 创建（申请）时间。 */
    @Column(name = "accepted_at", updatable = false)
    private OffsetDateTime acceptedAt;

    /** 审批落定时间（通过/驳回）。 */
    @Column(name = "decided_at")
    private OffsetDateTime decidedAt;

    /** JPA 要求的无参构造。 */
    protected RiskAcceptance() {
    }

    /** 业务构造：登记一条 PENDING 的风险接受申请。 */
    public RiskAcceptance(Long orgId, Long findingId, String requester, String reason) {
        this.orgId = orgId;
        this.findingId = findingId;
        this.requester = requester;
        this.reason = reason;
        this.status = AcceptanceStatus.PENDING;
    }

    @PrePersist
    void onCreate() {
        if (this.acceptedAt == null) {
            this.acceptedAt = OffsetDateTime.now();
        }
    }

    /** 审批通过：落定审批人与时间，状态转 APPROVED（由 Service 在校验后调用并回填放行凭据）。 */
    void approve(String approver) {
        this.status = AcceptanceStatus.APPROVED;
        this.approver = approver;
        this.decidedAt = OffsetDateTime.now();
    }

    /** 审批驳回：落定审批人与时间，状态转 REJECTED（不放行）。 */
    void reject(String approver) {
        this.status = AcceptanceStatus.REJECTED;
        this.approver = approver;
        this.decidedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public Long getOrgId() { return orgId; }
    public Long getFindingId() { return findingId; }
    public String getRequester() { return requester; }
    public String getReason() { return reason; }
    public AcceptanceStatus getStatus() { return status; }
    public String getApprover() { return approver; }
    public OffsetDateTime getAcceptedAt() { return acceptedAt; }
    public OffsetDateTime getDecidedAt() { return decidedAt; }
}
