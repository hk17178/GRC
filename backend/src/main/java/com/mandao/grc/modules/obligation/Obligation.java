package com.mandao.grc.modules.obligation;

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
 * 合规义务（合规清单·义务库）。
 *
 * 携带 org_id 隔离锚点；可见性/可写性由 RLS 依据 app.visible_orgs 自动裁剪。
 * 一条义务来源于某法规/标准（sourceRef 软引用），由责任部门在期限内落实并留证据；
 * 状态机见 {@link ObligationStatus}。
 */
@Entity
@Table(name = "obligation")
public class Obligation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 隔离锚点：所属组织。 */
    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    /** 义务编号（组织内唯一）。 */
    @Column(nullable = false, length = 64)
    private String code;

    /** 义务标题。 */
    @Column(nullable = false, length = 256)
    private String title;

    /** 来源（法规/标准编号，软引用）。 */
    @Column(name = "source_ref", length = 128)
    private String sourceRef;

    /** 分类。 */
    @Column(length = 64)
    private String category;

    /** 具体要求。 */
    @Column(columnDefinition = "TEXT")
    private String requirement;

    /** 责任部门。 */
    @Column(name = "owner_dept", length = 64)
    private String ownerDept;

    /** 落实期限。 */
    @Column(name = "due_date")
    private LocalDate dueDate;

    /** 状态机当前态。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ObligationStatus status = ObligationStatus.PENDING;

    /** 落实证据（FULFILLED 时必填）。 */
    @Column(columnDefinition = "TEXT")
    private String evidence;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    /** JPA 要求的无参构造。 */
    protected Obligation() {
    }

    /** 业务构造：以 PENDING 态登记一条合规义务。 */
    public Obligation(Long orgId, String code, String title, String sourceRef, String category,
                      String requirement, String ownerDept, LocalDate dueDate) {
        this.orgId = orgId;
        this.code = code;
        this.title = title;
        this.sourceRef = sourceRef;
        this.category = category;
        this.requirement = requirement;
        this.ownerDept = ownerDept;
        this.dueDate = dueDate;
        this.status = ObligationStatus.PENDING;
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

    /** 由 Service 在校验后推进状态机。 */
    void setStatus(ObligationStatus status) {
        this.status = status;
    }

    /** 由 Service 在落实时回写证据。 */
    void setEvidence(String evidence) {
        this.evidence = evidence;
    }

    public Long getId() { return id; }
    public Long getOrgId() { return orgId; }
    public String getCode() { return code; }
    public String getTitle() { return title; }
    public String getSourceRef() { return sourceRef; }
    public String getCategory() { return category; }
    public String getRequirement() { return requirement; }
    public String getOwnerDept() { return ownerDept; }
    public LocalDate getDueDate() { return dueDate; }
    public ObligationStatus getStatus() { return status; }
    public String getEvidence() { return evidence; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
