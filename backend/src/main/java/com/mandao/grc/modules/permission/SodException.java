package com.mandao.grc.modules.permission;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * SoD 豁免（M8，org-scoped）：经审批后放行原本被互斥红线（{@link SodRule}）拦截的授权。
 * 映射 V7 org-scoped 表 sod_exception。
 *
 * 一条豁免针对 (org, user, sodRule)：存在有效豁免时，{@link PermissionService#grantRole}
 * 对该规则的互斥判定放行。
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

    /** 审批人。 */
    @Column(nullable = false, length = 64)
    private String approver;

    /** 豁免理由。 */
    @Column
    private String reason;

    @Column(name = "approved_at", nullable = false)
    private OffsetDateTime approvedAt;

    protected SodException() {
    }

    public SodException(Long orgId, Long userId, Long sodRuleId, String approver, String reason) {
        this.orgId = orgId;
        this.userId = userId;
        this.sodRuleId = sodRuleId;
        this.approver = approver;
        this.reason = reason;
    }

    @PrePersist
    void onCreate() {
        if (this.approvedAt == null) {
            this.approvedAt = OffsetDateTime.now();
        }
    }

    public Long getId() { return id; }
    public Long getOrgId() { return orgId; }
    public Long getUserId() { return userId; }
    public Long getSodRuleId() { return sodRuleId; }
    public String getApprover() { return approver; }
    public String getReason() { return reason; }
    public OffsetDateTime getApprovedAt() { return approvedAt; }
}
