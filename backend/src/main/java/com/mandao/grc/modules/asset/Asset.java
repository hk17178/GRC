package com.mandao.grc.modules.asset;

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
 * 资产台账（M6 资产台账，含资产合规属性 CR-002），映射 V9 asset 表。
 *
 * 隔离锚点 org_id；可见/可写由 RLS 按 app.visible_orgs 自动裁剪（V9 已建 USING/WITH CHECK）。
 * 主键 BIGSERIAL，故用 {@link GenerationType#IDENTITY}。
 *
 * 资产合规属性（CR-002）：classification 分类分级、containsPi 个人信息、crossBorder 跨境、
 * mlpsFiled 等保备案、containsChd 持卡人数据——均可作为合规筛查维度（如查所有含 PI 的资产）。
 *
 * 状态机：ACTIVE → RETIRED（见 {@link AssetService}）。
 *
 * 设计依据：需求文档 M6、CR-002、D1-2、D2-5。
 */
@Entity
@Table(name = "asset")
public class Asset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 隔离锚点：所属组织。 */
    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    /** 资产名称。 */
    @Column(nullable = false, length = 256)
    private String name;

    /** 资产类型（SYSTEM/APP/DATABASE/DEVICE 等）。 */
    @Column(name = "asset_type", length = 24)
    private String assetType;

    /** 资产责任人。 */
    @Column(length = 64)
    private String owner;

    // ---------- 资产合规属性（CR-002） ----------

    /** 分类分级。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AssetClassification classification = AssetClassification.INTERNAL;

    /** 是否含个人信息（PI）。 */
    @Column(name = "contains_pi", nullable = false)
    private boolean containsPi;

    /** 是否涉及数据跨境。 */
    @Column(name = "cross_border", nullable = false)
    private boolean crossBorder;

    /** 是否已等保备案（MLPS）。 */
    @Column(name = "mlps_filed", nullable = false)
    private boolean mlpsFiled;

    /** 是否含持卡人数据（CHD）。 */
    @Column(name = "contains_chd", nullable = false)
    private boolean containsChd;

    /** 重要程度（LOW/MEDIUM/HIGH/CRITICAL）。 */
    @Column(length = 12)
    private String criticality;

    // ---------- 合规属性深化（M2 深度包 B47） ----------

    /** 等保定级（1~4 级；未定级为 null）。 */
    @Column(name = "mlps_level")
    private Integer mlpsLevel;

    /** 等保测评到期日（三级及以上须每年测评；到期扫描 30/7/0 天提醒）。 */
    @Column(name = "mlps_review_due")
    private java.time.LocalDate mlpsReviewDue;

    /** CIA 三性评级（如 3-3-2：机密性-完整性-可用性 各 1~3）。 */
    @Column(name = "cia_rating", length = 16)
    private String ciaRating;

    /** 网络区域（如 生产核心区/DMZ/办公网/托管机房）。 */
    @Column(name = "network_zone", length = 64)
    private String networkZone;

    /** 自定义字段值（B12 Phase1：键=custom_field_def.field_key，随本行 org_id 隔离）。 */
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private java.util.Map<String, Object> ext = new java.util.LinkedHashMap<>();

    /** 资产生命周期状态机当前态。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AssetStatus status = AssetStatus.ACTIVE;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    /** JPA 要求的无参构造。 */
    protected Asset() {
    }

    /** 业务构造：以 ACTIVE 态登记资产（含合规属性）。 */
    public Asset(Long orgId, String name, String assetType, String owner,
                 AssetClassification classification, boolean containsPi, boolean crossBorder,
                 boolean mlpsFiled, boolean containsChd, String criticality) {
        this.orgId = orgId;
        this.name = name;
        this.assetType = assetType;
        this.owner = owner;
        this.classification = classification == null ? AssetClassification.INTERNAL : classification;
        this.containsPi = containsPi;
        this.crossBorder = crossBorder;
        this.mlpsFiled = mlpsFiled;
        this.containsChd = containsChd;
        this.criticality = criticality;
        this.status = AssetStatus.ACTIVE;
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
    public String getAssetType() { return assetType; }
    public String getOwner() { return owner; }
    public AssetClassification getClassification() { return classification; }
    public boolean isContainsPi() { return containsPi; }
    public boolean isCrossBorder() { return crossBorder; }
    public boolean isMlpsFiled() { return mlpsFiled; }
    public boolean isContainsChd() { return containsChd; }
    public String getCriticality() { return criticality; }
    public Integer getMlpsLevel() { return mlpsLevel; }
    public java.time.LocalDate getMlpsReviewDue() { return mlpsReviewDue; }
    public String getCiaRating() { return ciaRating; }
    public String getNetworkZone() { return networkZone; }
    public java.util.Map<String, Object> getExt() { return ext; }
    public AssetStatus getStatus() { return status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    // 包级可见的合规属性变更入口，仅由 Service 在校验后调用。
    void setName(String name) { this.name = name; }
    void setAssetType(String assetType) { this.assetType = assetType; }
    void setOwner(String owner) { this.owner = owner; }
    void setClassification(AssetClassification classification) { this.classification = classification; }
    void setContainsPi(boolean containsPi) { this.containsPi = containsPi; }
    void setCrossBorder(boolean crossBorder) { this.crossBorder = crossBorder; }
    void setMlpsFiled(boolean mlpsFiled) { this.mlpsFiled = mlpsFiled; }
    void setContainsChd(boolean containsChd) { this.containsChd = containsChd; }
    void setCriticality(String criticality) { this.criticality = criticality; }
    void setMlpsLevel(Integer mlpsLevel) { this.mlpsLevel = mlpsLevel; }
    void setMlpsReviewDue(java.time.LocalDate mlpsReviewDue) { this.mlpsReviewDue = mlpsReviewDue; }
    void setCiaRating(String ciaRating) { this.ciaRating = ciaRating; }
    void setNetworkZone(String networkZone) { this.networkZone = networkZone; }
    void setExt(java.util.Map<String, Object> ext) { this.ext = ext == null ? new java.util.LinkedHashMap<>() : ext; }
    void setStatus(AssetStatus status) { this.status = status; }
}
