package com.mandao.grc.modules.notify;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * 通知中心配置（单表承载 通知场景 / 通知规则 / 通道，kind 区分）。
 *
 * 携 org_id 隔离锚点，RLS 按 visible_orgs 裁剪。detail 为各 kind 专有字段的 JSON 文本。
 */
@Entity
@Table(name = "notify_config")
public class NotifyConfig {

    public static final String SCENARIO = "SCENARIO";
    public static final String RULE = "RULE";
    public static final String CHANNEL = "CHANNEL";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    @Column(nullable = false, length = 16)
    private String kind;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String detail;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    protected NotifyConfig() {
    }

    public NotifyConfig(Long orgId, String kind, String name, String detail) {
        this.orgId = orgId;
        this.kind = kind;
        this.name = name;
        this.detail = detail;
    }

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = OffsetDateTime.now();
        }
    }

    public void update(String name, String detail) {
        this.name = name;
        this.detail = detail;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Long getId() { return id; }
    public Long getOrgId() { return orgId; }
    public String getKind() { return kind; }
    public String getName() { return name; }
    public String getDetail() { return detail; }
    public boolean isEnabled() { return enabled; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
