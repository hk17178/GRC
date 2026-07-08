package com.mandao.grc.modules.notify;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * 通知场景库（设计态，D1-8 §九），映射 V75 notif_scene_def 表。
 *
 * 全局字典（无 org_id）：登记"可装配的场景种类"及其涵盖的事件类型集。管理员从库里挑一个 def，
 * 配上本组织的角色/模板/通道，即装配成一个运行态 {@link NotificationScene}——新增场景无需改码。
 */
@Entity
@Table(name = "notif_scene_def")
public class NotifSceneDef {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 48, unique = true)
    private String code;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(name = "event_types", nullable = false, columnDefinition = "TEXT")
    private String eventTypes;

    @Column(length = 256)
    private String description;

    @Column(nullable = false)
    private boolean builtin = true;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    protected NotifSceneDef() {
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getEventTypes() { return eventTypes; }
    public String getDescription() { return description; }
    public boolean isBuiltin() { return builtin; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
