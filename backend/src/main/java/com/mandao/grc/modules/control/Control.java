package com.mandao.grc.modules.control;

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
 * 统一控制项（M2 统一控件库）。
 *
 * 携带 org_id 隔离锚点；可见性/可写性由 RLS 依据 app.visible_orgs 自动裁剪。
 *
 * "统一"体现为：一个控制项可经 {@link ControlFrameworkRef} 映射到多个合规框架（等保/ISO/PCI/PBOC）的条款，
 * 一次定义、多框架复用，避免各框架各建一套控制项。
 */
@Entity
@Table(name = "control_item")
public class Control {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 隔离锚点：所属组织。 */
    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    /** 控制项编码（组织内唯一，如 CTL-ACL-001）。 */
    @Column(nullable = false, length = 32)
    private String code;

    /** 控制项名称。 */
    @Column(nullable = false, length = 128)
    private String name;

    /** 控制描述/要求。 */
    @Column(columnDefinition = "TEXT")
    private String description;

    /** 控制域/分类（如 访问控制、加密、日志审计）。 */
    @Column(length = 64)
    private String domain;

    /** 状态（ACTIVE/RETIRED）。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ControlStatus status = ControlStatus.ACTIVE;

    /** 责任人（可空）。 */
    @Column(length = 64)
    private String owner;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    /** JPA 要求的无参构造。 */
    protected Control() {
    }

    /** 业务构造：以 ACTIVE 态定义一个控制项。 */
    public Control(Long orgId, String code, String name, String description, String domain, String owner) {
        this.orgId = orgId;
        this.code = code;
        this.name = name;
        this.description = description;
        this.domain = domain;
        this.owner = owner;
        this.status = ControlStatus.ACTIVE;
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

    /** 停用控制项（由 Service 调用）。 */
    void retire() {
        this.status = ControlStatus.RETIRED;
    }

    public Long getId() { return id; }
    public Long getOrgId() { return orgId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getDomain() { return domain; }
    public ControlStatus getStatus() { return status; }
    public String getOwner() { return owner; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
