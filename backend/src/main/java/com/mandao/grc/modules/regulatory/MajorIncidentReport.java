package com.mandao.grc.modules.regulatory;

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
 * 重大事件报送台账（M11 监管事项），映射 V8 major_incident_report 表。
 *
 * 隔离锚点 org_id；可见/可写由 RLS 按 app.visible_orgs 自动裁剪（V8 已建 USING/WITH CHECK）。
 * 状态机：DRAFT → REPORTED → CLOSED（见 {@link MajorIncidentService}）。
 *
 * 设计依据：需求文档 M11、D1-2 §23、D2-5。
 */
@Entity
@Table(name = "major_incident_report")
public class MajorIncidentReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    @Column(length = 256)
    private String title;

    /** 严重度（平台五级：VERY_LOW/LOW/MID/HIGH/VERY_HIGH）。 */
    @Enumerated(EnumType.STRING)
    @Column(length = 12)
    private MajorIncidentSeverity severity;

    /** 事件发生时刻。 */
    @Column(name = "occurred_at")
    private OffsetDateTime occurredAt;

    /** 上报监管时刻（report 流转时记录）。 */
    @Column(name = "reported_at")
    private OffsetDateTime reportedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MajorIncidentStatus status = MajorIncidentStatus.DRAFT;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    protected MajorIncidentReport() {
    }

    /** 业务构造：以 DRAFT 态新建重大事件报送。 */
    public MajorIncidentReport(Long orgId, String title, MajorIncidentSeverity severity, OffsetDateTime occurredAt) {
        this.orgId = orgId;
        this.title = title;
        this.severity = severity;
        this.occurredAt = occurredAt;
        this.status = MajorIncidentStatus.DRAFT;
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
    public String getTitle() { return title; }
    public MajorIncidentSeverity getSeverity() { return severity; }
    public OffsetDateTime getOccurredAt() { return occurredAt; }
    public OffsetDateTime getReportedAt() { return reportedAt; }
    public MajorIncidentStatus getStatus() { return status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    void setStatus(MajorIncidentStatus status) { this.status = status; }
    void setReportedAt(OffsetDateTime reportedAt) { this.reportedAt = reportedAt; }
}
