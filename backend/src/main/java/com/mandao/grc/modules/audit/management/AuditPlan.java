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

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 审计计划（M3 审计管理主实体），映射 V3 既有 audit_plan 表（V6 扩展列）。
 *
 * 隔离锚点 org_id；可见/可写由 RLS 依据 app.visible_orgs 自动裁剪（USING/WITH CHECK，V3 已建）。
 * 主键为 BIGSERIAL（V3 建），故用 {@link GenerationType#IDENTITY}。
 *
 * 与 V3 调度内核兼容：
 *  - {@code planStartDate}/{@code externalStatus} 为 V3 既有列，本实体保留映射，
 *    新建计划时写入合理默认（externalStatus='PLANNED' 以便 ExpiryScanService 仍能扫描产临近提醒）；
 *  - {@code reminderDays}（V3 INT[] 列）有库级 DEFAULT '{15,10}'，由 Service 显式 INSERT 时按 V3 默认填充，
 *    本实体不映射该数组列（避免 Hibernate 处理 PG 数组类型的额外配置），交由库默认值保障调度可用。
 *
 * 状态机（M3 业务生命周期）：PLANNED → IN_PROGRESS → REPORTING → CLOSED；
 * 另允许 PLANNED/IN_PROGRESS → CANCELLED（见 {@link AuditPlanService}）。
 */
@Entity
@Table(name = "audit_plan")
public class AuditPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 隔离锚点：所属组织。 */
    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    /** 审计计划标题。 */
    @Column(length = 256)
    private String title;

    /** 审计类型（内审/外审/监管检查/认证审计）。唯 EXTERNAL 的发现可走对外回函漏斗。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "audit_type", nullable = false, length = 16)
    private AuditType auditType;

    /** 计划开始日（V3 既有列，NOT NULL）；调度内核据此 + reminder_days 产临近提醒。 */
    @Column(name = "plan_start_date", nullable = false)
    private LocalDate planStartDate;

    /** V3 既有列：调度触发开关（PLANNED 时才被 ExpiryScanService 扫描）。新建默认 PLANNED。 */
    @Column(name = "external_status", nullable = false, length = 24)
    private String externalStatus = "PLANNED";

    /** M3 业务生命周期状态机当前态。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private AuditPlanStatus status = AuditPlanStatus.PLANNED;

    /** 绑定的检查表模板（V40，复用表单引擎 assessment_template；未绑定为空）。 */
    @Column(name = "checklist_template_id")
    private Long checklistTemplateId;

    /** 执行检查表产生的评估 id（V40；未执行为空）。 */
    @Column(name = "checklist_assessment_id")
    private Long checklistAssessmentId;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    /** JPA 要求的无参构造。 */
    protected AuditPlan() {
    }

    /** 业务构造：以 PLANNED 态新建审计计划。 */
    public AuditPlan(Long orgId, String title, AuditType auditType, LocalDate planStartDate) {
        this.orgId = orgId;
        this.title = title;
        this.auditType = auditType;
        this.planStartDate = planStartDate;
        this.externalStatus = "PLANNED";
        this.status = AuditPlanStatus.PLANNED;
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
    public String getTitle() { return title; }
    public AuditType getAuditType() { return auditType; }
    public LocalDate getPlanStartDate() { return planStartDate; }
    public String getExternalStatus() { return externalStatus; }
    public AuditPlanStatus getStatus() { return status; }
    public Long getChecklistTemplateId() { return checklistTemplateId; }
    public Long getChecklistAssessmentId() { return checklistAssessmentId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    // 以下 setter 为包级可见，仅由 Service 在校验后调用，封装状态变更。
    void setStatus(AuditPlanStatus status) { this.status = status; }
    void setChecklistTemplateId(Long templateId) { this.checklistTemplateId = templateId; }
    void setChecklistAssessmentId(Long assessmentId) { this.checklistAssessmentId = assessmentId; }
}
