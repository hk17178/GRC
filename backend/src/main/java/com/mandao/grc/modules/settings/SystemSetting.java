package com.mandao.grc.modules.settings;

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
 * 系统设置项（系统设置·租户配置 / D1-8 可配置性）。
 *
 * 携带 org_id 隔离锚点；可见性/可写性由 RLS 依据 app.visible_orgs 自动裁剪——各子公司可有各自配置。
 * 键值对模型：settingKey 唯一键、settingValue 取值（按 valueType 校验）、category 分组、
 * editable 标明是否允许修改（系统锁定项 editable=false 不可改）。
 *
 * 设计依据：需求文档 系统设置、D1-8 可配置性低代码专项、D2-5。
 */
@Entity
@Table(name = "system_setting")
public class SystemSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 隔离锚点：所属组织（租户）。 */
    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    /** 配置键（组织内唯一，如 reminder.days、risk.matrix.high）。 */
    @Column(name = "setting_key", nullable = false, length = 128)
    private String settingKey;

    /** 配置值（文本，按 valueType 解释）。 */
    @Column(name = "setting_value", columnDefinition = "TEXT")
    private String settingValue;

    /** 值类型。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "value_type", nullable = false, length = 16)
    private SettingValueType valueType;

    /** 分组（如 reminder、risk、security）。 */
    @Column(length = 64)
    private String category;

    /** 说明。 */
    @Column(columnDefinition = "TEXT")
    private String description;

    /** 是否可修改（false=系统锁定项，不可改）。 */
    @Column(nullable = false)
    private boolean editable = true;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    /** JPA 要求的无参构造。 */
    protected SystemSetting() {
    }

    /** 业务构造：定义一个配置项。 */
    public SystemSetting(Long orgId, String settingKey, String settingValue, SettingValueType valueType,
                         String category, String description, boolean editable) {
        this.orgId = orgId;
        this.settingKey = settingKey;
        this.settingValue = settingValue;
        this.valueType = valueType;
        this.category = category;
        this.description = description;
        this.editable = editable;
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

    /** 由 Service 在校验（可改 + 类型）后更新取值。 */
    void setSettingValue(String settingValue) {
        this.settingValue = settingValue;
    }

    public Long getId() { return id; }
    public Long getOrgId() { return orgId; }
    public String getSettingKey() { return settingKey; }
    public String getSettingValue() { return settingValue; }
    public SettingValueType getValueType() { return valueType; }
    public String getCategory() { return category; }
    public String getDescription() { return description; }
    public boolean isEditable() { return editable; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
