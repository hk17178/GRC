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
 * 自定义 KPI 定义（B12 Phase4 / D1-8 §七），映射 V72 kpi_def 表。
 *
 * formula 为声明式 JSON DSL（terms 带筛选标量聚合 + expr 受限算术 + unit），绝不存裸 SQL/代码；
 * 运行期由 {@link KpiDefService} 求值引擎在注入 visible_orgs 的 RLS 会话执行。
 * 隔离锚点 org_id；可见/可写由 RLS 按 app.visible_orgs 自动裁剪。
 */
@Entity
@Table(name = "kpi_def")
public class KpiDef {

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
    private String formula;

    @Column(length = 16)
    private String unit;

    @Column(nullable = false, length = 16)
    private String status = "ACTIVE";

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    protected KpiDef() {
    }

    public KpiDef(Long orgId, String objectType, String name, String formula, String unit, String createdBy) {
        this.orgId = orgId;
        this.objectType = objectType;
        this.name = name;
        this.formula = formula;
        this.unit = unit;
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
    public String getFormula() { return formula; }
    public String getUnit() { return unit; }
    public String getStatus() { return status; }
    public String getCreatedBy() { return createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    void setStatus(String status) { this.status = status; }
}
