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
 * 自定义字段定义（B12 低代码 Phase1 / D1-8 H-04），映射 V69 custom_field_def 表。
 *
 * 客户不改代码即可为宿主对象（本期 ASSET）登记自定义字段；字段值落宿主表 ext JSONB。
 * 隔离锚点 org_id；可见/可写由 RLS 按 app.visible_orgs 自动裁剪。
 *
 * 红线（D1-8 §一）：自定义能力绝不绕过组织隔离——def 携 org_id + RLS，ext 值随宿主行隔离。
 */
@Entity
@Table(name = "custom_field_def")
public class CustomFieldDef {

    /** 支持的数据类型。 */
    public enum DataType { TEXT, NUMBER, DATE, BOOL, SELECT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    @Column(name = "object_type", nullable = false, length = 32)
    private String objectType;

    @Column(name = "field_key", nullable = false, length = 64)
    private String fieldKey;

    @Column(nullable = false, length = 128)
    private String label;

    @Column(name = "data_type", nullable = false, length = 16)
    private String dataType;

    @Column(columnDefinition = "TEXT")
    private String options;

    @Column(nullable = false)
    private boolean required;

    @Column(name = "is_sensitive", nullable = false)
    private boolean sensitive;

    @Column(name = "is_aggregatable", nullable = false)
    private boolean aggregatable;

    @Column(nullable = false)
    private int seq;

    @Column(nullable = false, length = 16)
    private String status = "ACTIVE";

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    protected CustomFieldDef() {
    }

    public CustomFieldDef(Long orgId, String objectType, String fieldKey, String label, DataType dataType,
                          String options, boolean required, boolean sensitive, boolean aggregatable,
                          int seq, String createdBy) {
        this.orgId = orgId;
        this.objectType = objectType;
        this.fieldKey = fieldKey;
        this.label = label;
        this.dataType = dataType.name();
        this.options = options;
        this.required = required;
        this.sensitive = sensitive;
        this.aggregatable = aggregatable;
        this.seq = seq;
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
    public String getFieldKey() { return fieldKey; }
    public String getLabel() { return label; }
    public String getDataType() { return dataType; }
    public String getOptions() { return options; }
    public boolean isRequired() { return required; }
    public boolean isSensitive() { return sensitive; }
    public boolean isAggregatable() { return aggregatable; }
    public int getSeq() { return seq; }
    public String getStatus() { return status; }
    public String getCreatedBy() { return createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    void setStatus(String status) { this.status = status; }
}
