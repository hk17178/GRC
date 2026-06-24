package com.mandao.grc.modules.regulation;

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
 * 法规（法规跟踪·法规库）。
 *
 * 携带 org_id 隔离锚点；可见性/可写性由 RLS 依据 app.visible_orgs 自动裁剪。
 * 状态机 TRACKING → EFFECTIVE → SUPERSEDED/ABOLISHED（{@link RegulationStatus}）；
 * 法规动态以 {@link RegulationChange} 追踪，每条变更须完成影响评估方闭环。
 */
@Entity
@Table(name = "regulation")
public class Regulation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 隔离锚点：所属组织。 */
    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    /** 法规编号（组织内唯一）。 */
    @Column(nullable = false, length = 64)
    private String code;

    /** 法规标题。 */
    @Column(nullable = false, length = 256)
    private String title;

    /** 发布机构（如 人民银行、网信办）。 */
    @Column(length = 64)
    private String issuer;

    /** 分类（如 反洗钱、数据安全、支付结算）。 */
    @Column(length = 64)
    private String category;

    /** 状态机当前态。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RegulationStatus status = RegulationStatus.TRACKING;

    /** 生效日期（可空，跟踪阶段未定）。 */
    @Column(name = "effective_date")
    private LocalDate effectiveDate;

    /** 摘要。 */
    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    /** JPA 要求的无参构造。 */
    protected Regulation() {
    }

    /** 业务构造：以 TRACKING 态登记一条法规。 */
    public Regulation(Long orgId, String code, String title, String issuer, String category,
                      LocalDate effectiveDate, String summary) {
        this.orgId = orgId;
        this.code = code;
        this.title = title;
        this.issuer = issuer;
        this.category = category;
        this.effectiveDate = effectiveDate;
        this.summary = summary;
        this.status = RegulationStatus.TRACKING;
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

    /** 由 Service 在校验后推进状态机。 */
    void setStatus(RegulationStatus status) {
        this.status = status;
    }

    public Long getId() { return id; }
    public Long getOrgId() { return orgId; }
    public String getCode() { return code; }
    public String getTitle() { return title; }
    public String getIssuer() { return issuer; }
    public String getCategory() { return category; }
    public RegulationStatus getStatus() { return status; }
    public LocalDate getEffectiveDate() { return effectiveDate; }
    public String getSummary() { return summary; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
