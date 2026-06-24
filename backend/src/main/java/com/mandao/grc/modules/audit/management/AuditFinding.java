package com.mandao.grc.modules.audit.management;

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
 * 审计发现（M3 审计管理子实体）。
 *
 * 隔离锚点 org_id；可见/可写由 RLS 依据 app.visible_orgs 自动裁剪。主键 BIGSERIAL → {@link GenerationType#IDENTITY}。
 *
 * 两条独立轨：
 *  - {@code status}：内部处置状态机 OPEN→ANALYZING→REMEDIATED→CLOSED；
 *  - {@code externalResponseStatus}：外部审计对外回函三段漏斗（红线，仅外审发现可用，可空）
 *    SUBMITTED→ACCEPTED→CONFIRMED_CLOSED，单向、不可跳级、不可逆向，唯 CONFIRMED_CLOSED 算外审闭环。
 *    漏斗推进/校验见 {@link AuditFindingService}。
 */
@Entity
@Table(name = "audit_finding")
public class AuditFinding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 隔离锚点：所属组织。 */
    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    /** 所属审计计划。 */
    @Column(name = "audit_plan_id", nullable = false, updatable = false)
    private Long auditPlanId;

    /** 发现描述。 */
    @Column(length = 256)
    private String title;

    /** 严重度（LOW/MID/HIGH/CRITICAL）。 */
    @Enumerated(EnumType.STRING)
    @Column(length = 12)
    private AuditSeverity severity;

    /** 内部处置状态机当前态。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AuditFindingStatus status = AuditFindingStatus.OPEN;

    /** 外审回函三段漏斗当前段（仅外审用，可空：未进入漏斗为 null）。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "external_response_status", length = 24)
    private ExternalResponseStatus externalResponseStatus;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    /** JPA 要求的无参构造。 */
    protected AuditFinding() {
    }

    /** 业务构造：以 OPEN 态新建审计发现（漏斗未进入，externalResponseStatus 为 null）。 */
    public AuditFinding(Long orgId, Long auditPlanId, String title, AuditSeverity severity) {
        this.orgId = orgId;
        this.auditPlanId = auditPlanId;
        this.title = title;
        this.severity = severity;
        this.status = AuditFindingStatus.OPEN;
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
    public Long getAuditPlanId() { return auditPlanId; }
    public String getTitle() { return title; }
    public AuditSeverity getSeverity() { return severity; }
    public AuditFindingStatus getStatus() { return status; }
    public ExternalResponseStatus getExternalResponseStatus() { return externalResponseStatus; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    // 以下 setter 为包级可见，仅由 Service 在校验后调用，封装状态变更。
    void setSeverity(AuditSeverity severity) { this.severity = severity; }
    void setStatus(AuditFindingStatus status) { this.status = status; }
    void setExternalResponseStatus(ExternalResponseStatus externalResponseStatus) {
        this.externalResponseStatus = externalResponseStatus;
    }
}
