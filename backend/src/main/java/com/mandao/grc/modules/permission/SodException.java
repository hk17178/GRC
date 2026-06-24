package com.mandao.grc.modules.permission;

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
 * SoD 豁免（M8，org-scoped）：经【审批通过】后放行原本被互斥红线（{@link SodRule}）拦截的授权。
 * 映射 V7 org-scoped 表 sod_exception（V17 增审批字段）。
 *
 * ===== 审批化（A4）=====
 * 改为「申请→审批」两段式：申请置 {@link SodExceptionStatus#PENDING}（不放行）；经 Flowable 审批通过
 * {@link #approve} 才置 APPROVED 并作为有效豁免；驳回 {@link #reject} 置 REJECTED、不放行。
 * {@link PermissionService#enforceSod} 仅认 APPROVED 豁免——即 SoD 豁免须经审批通过方生效。
 *
 * 隔离锚点 org_id（USING/WITH CHECK，V7 已建）；主键 BIGSERIAL，用 {@link GenerationType#IDENTITY}。
 *
 * 设计依据：需求文档 M8 权限审批（SoD 豁免）、D1-3 §4.7、D2-5。
 */
@Entity
@Table(name = "sod_exception")
public class SodException {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 隔离锚点：豁免所在组织。 */
    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    /** 被豁免用户（app_user.id）。 */
    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    /** 所豁免的互斥规则（sod_rule.id）。 */
    @Column(name = "sod_rule_id", nullable = false, updatable = false)
    private Long sodRuleId;

    /** 申请人。 */
    @Column(length = 64)
    private String requester;

    /** 豁免理由。 */
    @Column
    private String reason;

    /** 审批状态（PENDING/APPROVED/REJECTED）。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SodExceptionStatus status = SodExceptionStatus.PENDING;

    /** 审批人（通过/驳回时落定，PENDING 时为空）。 */
    @Column(length = 64)
    private String approver;

    /** 审批落定时间（通过/驳回时）。 */
    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    protected SodException() {
    }

    /** 业务构造：登记一条 PENDING 的 SoD 豁免申请。 */
    public SodException(Long orgId, Long userId, Long sodRuleId, String requester, String reason) {
        this.orgId = orgId;
        this.userId = userId;
        this.sodRuleId = sodRuleId;
        this.requester = requester;
        this.reason = reason;
        this.status = SodExceptionStatus.PENDING;
    }

    @PrePersist
    void onCreate() {
        // 申请阶段不落定 approvedAt（审批通过/驳回时才置）。
    }

    /** 审批通过：状态转 APPROVED，落定审批人与时间（由 Service 校验后调用）。 */
    void approve(String approver) {
        this.status = SodExceptionStatus.APPROVED;
        this.approver = approver;
        this.approvedAt = OffsetDateTime.now();
    }

    /** 审批驳回：状态转 REJECTED，落定审批人与时间。 */
    void reject(String approver) {
        this.status = SodExceptionStatus.REJECTED;
        this.approver = approver;
        this.approvedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public Long getOrgId() { return orgId; }
    public Long getUserId() { return userId; }
    public Long getSodRuleId() { return sodRuleId; }
    public String getRequester() { return requester; }
    public String getReason() { return reason; }
    public SodExceptionStatus getStatus() { return status; }
    public String getApprover() { return approver; }
    public OffsetDateTime getApprovedAt() { return approvedAt; }
}
