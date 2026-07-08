package com.mandao.grc.modules.notify;

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
 * 运行态通知场景（装配，D1-8 §九），映射 V75 notification_scene 表。
 *
 * 由某个 {@link NotifSceneDef} 装配而来：事件集（来自 def）→ 接收角色/层级 → 模板 → 通道 → org_scope。
 * 隔离锚点 org_id + RLS；org_scope 仅 SELF/SUBTREE（本组织及下级），接收人始终在本组织子树内——不跨子公司广播。
 */
@Entity
@Table(name = "notification_scene")
public class NotificationScene {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    @Column(name = "scene_def_id", nullable = false)
    private Long sceneDefId;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(name = "recipient_roles", nullable = false, columnDefinition = "TEXT")
    private String recipientRoles;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String template;

    @Column(name = "channel_type", nullable = false, length = 16)
    private String channelType = "INBOX";

    @Column(name = "org_scope", nullable = false, length = 16)
    private String orgScope = "SELF";

    @Column(nullable = false, length = 16)
    private String status = "ACTIVE";

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    protected NotificationScene() {
    }

    public NotificationScene(Long orgId, Long sceneDefId, String name, String recipientRoles,
                             String template, String channelType, String orgScope, String createdBy) {
        this.orgId = orgId;
        this.sceneDefId = sceneDefId;
        this.name = name;
        this.recipientRoles = recipientRoles;
        this.template = template;
        this.channelType = channelType;
        this.orgScope = orgScope;
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
    public Long getSceneDefId() { return sceneDefId; }
    public String getName() { return name; }
    public String getRecipientRoles() { return recipientRoles; }
    public String getTemplate() { return template; }
    public String getChannelType() { return channelType; }
    public String getOrgScope() { return orgScope; }
    public String getStatus() { return status; }
    public String getCreatedBy() { return createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    void setStatus(String status) { this.status = status; }
}
