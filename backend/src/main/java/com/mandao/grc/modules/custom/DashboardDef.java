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
 * 自定义看板定义（B12 Phase5 / D1-8 §五），映射 V73 dashboard_def 表。
 *
 * layout 为声明式 JSON（widgets[]，每个组件 type=KPI/REPORT + refId + title）。组件不自取数——
 * 只能引用已登记的 kpi_def / custom_report_def，渲染时由 {@link CustomDashboardService} 逐组件解析（各走 RLS）。
 * 隔离锚点 org_id；可见/可写由 RLS 按 app.visible_orgs 自动裁剪。
 */
@Entity
@Table(name = "dashboard_def")
public class DashboardDef {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String layout;

    @Column(nullable = false, length = 16)
    private String status = "ACTIVE";

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    protected DashboardDef() {
    }

    public DashboardDef(Long orgId, String name, String layout, String createdBy) {
        this.orgId = orgId;
        this.name = name;
        this.layout = layout;
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
    public String getName() { return name; }
    public String getLayout() { return layout; }
    public String getStatus() { return status; }
    public String getCreatedBy() { return createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    void setStatus(String status) { this.status = status; }
}
