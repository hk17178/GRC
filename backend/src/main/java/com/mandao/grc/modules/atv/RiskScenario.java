package com.mandao.grc.modules.atv;

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
 * 风险场景（A-T-V：资产 × 威胁 × 脆弱性 → 一个可识别的风险）。
 *
 * 携带 org_id 隔离锚点；可见性/可写性由 RLS 自动裁剪。assetId 软引用 M6 资产（跨模块解耦，不设 FK，
 * 由 Service 校验资产可见）。固有等级由 可能性(likelihood) × 影响(impact) 经风险矩阵派生为平台五级。
 */
@Entity
@Table(name = "risk_scenario")
public class RiskScenario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 隔离锚点：所属组织。 */
    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    /** 资产（软引用 M6 asset）。 */
    @Column(name = "asset_id", nullable = false, updatable = false)
    private Long assetId;

    /** 威胁。 */
    @Column(name = "threat_id", nullable = false, updatable = false)
    private Long threatId;

    /** 脆弱性。 */
    @Column(name = "vulnerability_id", nullable = false, updatable = false)
    private Long vulnerabilityId;

    /** 可能性（1–5）。 */
    @Column(nullable = false)
    private Integer likelihood;

    /** 影响（1–5）。 */
    @Column(nullable = false)
    private Integer impact;

    /** 派生固有风险等级（平台五级）。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "inherent_level", nullable = false, length = 12)
    private RiskLevel inherentLevel;

    /** 场景描述。 */
    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    /** JPA 要求的无参构造。 */
    protected RiskScenario() {
    }

    /** 业务构造：登记一个 A-T-V 风险场景（固有等级由可能性×影响派生）。 */
    public RiskScenario(Long orgId, Long assetId, Long threatId, Long vulnerabilityId,
                        int likelihood, int impact, String description) {
        this.orgId = orgId;
        this.assetId = assetId;
        this.threatId = threatId;
        this.vulnerabilityId = vulnerabilityId;
        this.likelihood = likelihood;
        this.impact = impact;
        this.inherentLevel = deriveLevel(likelihood, impact);
        this.description = description;
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

    /**
     * 风险矩阵：可能性(1–5) × 影响(1–5) 的乘积(1–25) 分档映射到平台五级。
     * ≤4 极低；≤8 低；≤12 中；≤16 高；其余(20/25) 极高。
     */
    public static RiskLevel deriveLevel(int likelihood, int impact) {
        int score = likelihood * impact;
        if (score <= 4) {
            return RiskLevel.VERY_LOW;
        } else if (score <= 8) {
            return RiskLevel.LOW;
        } else if (score <= 12) {
            return RiskLevel.MID;
        } else if (score <= 16) {
            return RiskLevel.HIGH;
        } else {
            return RiskLevel.VERY_HIGH;
        }
    }

    /** 由 Service 在重评后回写可能性/影响并重算固有等级。 */
    void reassess(int likelihood, int impact) {
        this.likelihood = likelihood;
        this.impact = impact;
        this.inherentLevel = deriveLevel(likelihood, impact);
    }

    public Long getId() { return id; }
    public Long getOrgId() { return orgId; }
    public Long getAssetId() { return assetId; }
    public Long getThreatId() { return threatId; }
    public Long getVulnerabilityId() { return vulnerabilityId; }
    public Integer getLikelihood() { return likelihood; }
    public Integer getImpact() { return impact; }
    public RiskLevel getInherentLevel() { return inherentLevel; }
    public String getDescription() { return description; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
