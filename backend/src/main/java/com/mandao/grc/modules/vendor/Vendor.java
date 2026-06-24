package com.mandao.grc.modules.vendor;

import com.mandao.grc.modules.assessment.RiskLevel;
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
 * 第三方供应商（供应商管理）。
 *
 * 携带 org_id 隔离锚点；可见性/可写性由 RLS 依据 app.visible_orgs 自动裁剪。
 * 状态机 ONBOARDING → ACTIVE ⇄ SUSPENDED → TERMINATED；启用受准入门控（须先评估）。
 * riskLevel 为最近一次评估的风险等级（平台五级，{@link RiskLevel}），尚未评估时为空。
 */
@Entity
@Table(name = "vendor")
public class Vendor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 隔离锚点：所属组织。 */
    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    /** 供应商编号（组织内唯一）。 */
    @Column(nullable = false, length = 64)
    private String code;

    /** 供应商名称。 */
    @Column(nullable = false, length = 256)
    private String name;

    /** 服务类别（如 云服务、外包开发、数据处理）。 */
    @Column(length = 64)
    private String category;

    /** 联系方式。 */
    @Column(length = 128)
    private String contact;

    /** 状态机当前态。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private VendorStatus status = VendorStatus.ONBOARDING;

    /** 最近评估风险等级（五级；尚未评估为空）。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", length = 12)
    private RiskLevel riskLevel;

    /** 重要性（如 关键/重要/一般）。 */
    @Column(length = 16)
    private String criticality;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    /** JPA 要求的无参构造。 */
    protected Vendor() {
    }

    /** 业务构造：以 ONBOARDING 态登记一个供应商。 */
    public Vendor(Long orgId, String code, String name, String category, String contact, String criticality) {
        this.orgId = orgId;
        this.code = code;
        this.name = name;
        this.category = category;
        this.contact = contact;
        this.criticality = criticality;
        this.status = VendorStatus.ONBOARDING;
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
    void setStatus(VendorStatus status) {
        this.status = status;
    }

    /** 由 Service 在评估后回写最近风险等级。 */
    void setRiskLevel(RiskLevel riskLevel) {
        this.riskLevel = riskLevel;
    }

    public Long getId() { return id; }
    public Long getOrgId() { return orgId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public String getContact() { return contact; }
    public VendorStatus getStatus() { return status; }
    public RiskLevel getRiskLevel() { return riskLevel; }
    public String getCriticality() { return criticality; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
