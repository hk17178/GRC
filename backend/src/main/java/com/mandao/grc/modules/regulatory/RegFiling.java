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

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 报送日历（M11 监管事项主台账之一），映射 V8 reg_filing 表。
 *
 * 隔离锚点 org_id；可见/可写由 RLS 依据 app.visible_orgs 自动裁剪（USING/WITH CHECK，V8 已建）。
 * 主键 BIGSERIAL，故用 {@link GenerationType#IDENTITY}。
 *
 * 法定时限预警（红线）：statutory_deadline + reminder_days 为调度内核到期源——
 * {@code reminder_days}（V8 INT[] 列）有库级 DEFAULT '{15,10}'，本实体【不映射该数组列】
 * （避免 Hibernate 处理 PG 数组类型的额外配置），由库默认值兜底保障调度可用；
 * ExpiryScanService 命中 reminder_days 某天即产 REG_FILING_DUE。
 *
 * 状态机：TO_DRAFT → DRAFTING → SUBMITTED → CLOSED（见 {@link RegFilingService}）。
 *
 * 设计依据：需求文档 M11、D1-2 §23、D2-5。
 */
@Entity
@Table(name = "reg_filing")
public class RegFiling {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 隔离锚点：所属组织。 */
    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    /** 报送事项标题。 */
    @Column(length = 256)
    private String title;

    /** 监管机构。 */
    @Column(length = 64)
    private String regulator;

    /** 法定报送时限（NOT NULL；调度据此 + reminder_days 产法定时限预警）。 */
    @Column(name = "statutory_deadline", nullable = false)
    private LocalDate statutoryDeadline;

    /** 报送生命周期状态机当前态。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private RegFilingStatus status = RegFilingStatus.TO_DRAFT;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    /** JPA 要求的无参构造。 */
    protected RegFiling() {
    }

    /** 业务构造：以 TO_DRAFT 态新建报送事项。 */
    public RegFiling(Long orgId, String title, String regulator, LocalDate statutoryDeadline) {
        this.orgId = orgId;
        this.title = title;
        this.regulator = regulator;
        this.statutoryDeadline = statutoryDeadline;
        this.status = RegFilingStatus.TO_DRAFT;
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
    public String getRegulator() { return regulator; }
    public LocalDate getStatutoryDeadline() { return statutoryDeadline; }
    public RegFilingStatus getStatus() { return status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    /** 包级可见，仅由 Service 在校验后调用，封装状态变更。 */
    void setStatus(RegFilingStatus status) { this.status = status; }
}
