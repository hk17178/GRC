package com.mandao.grc.modules.custom;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * 自定义列表视图定义（B12 Phase2 / D1-8 H-05），映射 V70 custom_view_def 表。
 *
 * definition 为声明式 JSON（可见列/筛选/排序），绝不存裸 SQL；运行期由
 * {@link CustomViewService} 的编排器编译为参数化查询，强制字段白名单 + RLS 隔离。
 * 隔离锚点 org_id；可见/可写由 RLS 按 app.visible_orgs 自动裁剪。
 */
@Entity
@Table(name = "custom_view_def")
public class CustomViewDef {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    @Column(name = "object_type", nullable = false, length = 32)
    private String objectType;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String definition;

    @Column(nullable = false, length = 16)
    private String status = "ACTIVE";

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    protected CustomViewDef() {
    }

    public CustomViewDef(Long orgId, String objectType, String name, String definition, String createdBy) {
        this.orgId = orgId;
        this.objectType = objectType;
        this.name = name;
        this.definition = definition;
        this.createdBy = createdBy;
        this.status = "ACTIVE";
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
    public String getObjectType() { return objectType; }
    public String getName() { return name; }
    public String getDefinition() { return definition; }
    public String getStatus() { return status; }
    public String getCreatedBy() { return createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    void setStatus(String status) { this.status = status; }
}
