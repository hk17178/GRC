package com.mandao.grc.modules.assessment;

import com.mandao.grc.modules.control.ControlFramework;
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
 * 评估模板（M2 评估模板库）。
 *
 * 携带 org_id 隔离锚点；可见性/可写性由 RLS 依据 app.visible_orgs 自动裁剪。
 *
 * 模板面向某合规框架（{@link ControlFramework}），由若干 {@link AssessmentTemplateItem} 组成检查项；
 * 发布后可被「实例化」为一次评估（{@link Assessment}）及其评估项（{@link AssessmentItem}），实现复用。
 * 状态机：DRAFT → PUBLISHED → RETIRED（见 {@link TemplateStatus}）。
 */
@Entity
@Table(name = "assessment_template")
public class AssessmentTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 隔离锚点：所属组织。 */
    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    /** 模板编码（组织内唯一）。 */
    @Column(nullable = false, length = 32)
    private String code;

    /** 模板名称。 */
    @Column(nullable = false, length = 128)
    private String name;

    /** 面向的合规框架。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ControlFramework framework;

    /** 状态机当前态。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TemplateStatus status = TemplateStatus.DRAFT;

    /** 模板说明。 */
    @Column(columnDefinition = "TEXT")
    private String description;

    /** 责任人（可空）。 */
    @Column(length = 64)
    private String owner;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    /** JPA 要求的无参构造。 */
    protected AssessmentTemplate() {
    }

    /** 业务构造：以 DRAFT 态新建模板。 */
    public AssessmentTemplate(Long orgId, String code, String name, ControlFramework framework,
                              String description, String owner) {
        this.orgId = orgId;
        this.code = code;
        this.name = name;
        this.framework = framework;
        this.description = description;
        this.owner = owner;
        this.status = TemplateStatus.DRAFT;
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
    void setStatus(TemplateStatus status) {
        this.status = status;
    }

    public Long getId() { return id; }
    public Long getOrgId() { return orgId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public ControlFramework getFramework() { return framework; }
    public TemplateStatus getStatus() { return status; }
    public String getDescription() { return description; }
    public String getOwner() { return owner; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
