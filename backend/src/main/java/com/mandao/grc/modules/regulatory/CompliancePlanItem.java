package com.mandao.grc.modules.regulatory;

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
 * 年度合规计划项（年度计划下的单条合规工作事项）。
 *
 * 携带 org_id 隔离锚点（与所属计划同组织）；可见性/可写性由 RLS 自动裁剪。
 */
@Entity
@Table(name = "compliance_plan_item")
public class CompliancePlanItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 隔离锚点：所属组织。 */
    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    /** 所属年度计划。 */
    @Column(name = "plan_id", nullable = false, updatable = false)
    private Long planId;

    /** 排序序号。 */
    @Column(nullable = false)
    private Integer seq;

    /** 合规事项。 */
    @Column(columnDefinition = "TEXT")
    private String matter;

    /** 责任部门。 */
    @Column(name = "owner_dept", length = 64)
    private String ownerDept;

    /** 计划完成时间。 */
    @Column(name = "due_date")
    private LocalDate dueDate;

    /** 计划项状态。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CompliancePlanItemStatus status = CompliancePlanItemStatus.PENDING;

    /** 备注。 */
    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    /** JPA 要求的无参构造。 */
    protected CompliancePlanItem() {
    }

    /** 业务构造：以 PENDING 态新建计划项。 */
    public CompliancePlanItem(Long orgId, Long planId, Integer seq, String matter,
                              String ownerDept, LocalDate dueDate) {
        this.orgId = orgId;
        this.planId = planId;
        this.seq = seq;
        this.matter = matter;
        this.ownerDept = ownerDept;
        this.dueDate = dueDate;
        this.status = CompliancePlanItemStatus.PENDING;
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

    /** 由 Service 回写进度状态。 */
    void setStatus(CompliancePlanItemStatus status) {
        this.status = status;
    }

    public Long getId() { return id; }
    public Long getOrgId() { return orgId; }
    public Long getPlanId() { return planId; }
    public Integer getSeq() { return seq; }
    public String getMatter() { return matter; }
    public String getOwnerDept() { return ownerDept; }
    public LocalDate getDueDate() { return dueDate; }
    public CompliancePlanItemStatus getStatus() { return status; }
    public String getNote() { return note; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
