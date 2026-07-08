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
 * 通知升级链一级（D1-8 §九），映射 V75 notification_escalation 表。
 *
 * 附着于某个运行态 {@link NotificationScene}：到点（delay_hours）未处理则升级给 escalate_to_role。
 * 隔离锚点 org_id + RLS，随宿主场景同组织。
 */
@Entity
@Table(name = "notification_escalation")
public class NotificationEscalation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    @Column(name = "scene_id", nullable = false)
    private Long sceneId;

    @Column(nullable = false)
    private int level;

    @Column(name = "delay_hours", nullable = false)
    private int delayHours;

    @Column(name = "escalate_to_role", nullable = false, length = 48)
    private String escalateToRole;

    @Column(nullable = false, length = 16)
    private String status = "ACTIVE";

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    protected NotificationEscalation() {
    }

    public NotificationEscalation(Long orgId, Long sceneId, int level, int delayHours, String escalateToRole) {
        this.orgId = orgId;
        this.sceneId = sceneId;
        this.level = level;
        this.delayHours = delayHours;
        this.escalateToRole = escalateToRole;
        this.status = "ACTIVE";
    }

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = OffsetDateTime.now();
        }
    }

    public Long getId() { return id; }
    public Long getOrgId() { return orgId; }
    public Long getSceneId() { return sceneId; }
    public int getLevel() { return level; }
    public int getDelayHours() { return delayHours; }
    public String getEscalateToRole() { return escalateToRole; }
    public String getStatus() { return status; }
}
