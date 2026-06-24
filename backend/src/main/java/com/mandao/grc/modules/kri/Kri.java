package com.mandao.grc.modules.kri;

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

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 关键风险指标 KRI（M2 风险持续监测）。
 *
 * 携带 org_id 隔离锚点；可见性/可写性由 RLS 依据 app.visible_orgs 自动裁剪。
 *
 * 每个 KRI 配预警(warning)/严重(critical)双阈值与方向 {@link KriDirection}；每次测量按方向对阈值
 * 评定状态 {@link KriStatus}，并回写"最近值/最近状态"。CRITICAL 即红线触发。
 */
@Entity
@Table(name = "kri")
public class Kri {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 隔离锚点：所属组织。 */
    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    /** 指标编码（组织内唯一，如 KRI-VULN-001）。 */
    @Column(nullable = false, length = 32)
    private String code;

    /** 指标名称。 */
    @Column(nullable = false, length = 128)
    private String name;

    /** 计量单位（个/%/天等，可空）。 */
    @Column(length = 16)
    private String unit;

    /** 阈值方向（越高越坏/越低越坏）。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private KriDirection direction;

    /** 预警阈值。 */
    @Column(name = "threshold_warning", nullable = false, precision = 18, scale = 4)
    private BigDecimal thresholdWarning;

    /** 严重阈值。 */
    @Column(name = "threshold_critical", nullable = false, precision = 18, scale = 4)
    private BigDecimal thresholdCritical;

    /** 最近测量值（尚无测量时为空）。 */
    @Column(name = "current_value", precision = 18, scale = 4)
    private BigDecimal currentValue;

    /** 最近状态（尚无测量时 UNKNOWN）。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "current_status", nullable = false, length = 16)
    private KriStatus currentStatus = KriStatus.UNKNOWN;

    /** 指标责任人（可空）。 */
    @Column(length = 64)
    private String owner;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    /** JPA 要求的无参构造。 */
    protected Kri() {
    }

    /** 业务构造：定义一个 KRI（尚无测量，状态 UNKNOWN）。 */
    public Kri(Long orgId, String code, String name, String unit, KriDirection direction,
               BigDecimal thresholdWarning, BigDecimal thresholdCritical, String owner) {
        this.orgId = orgId;
        this.code = code;
        this.name = name;
        this.unit = unit;
        this.direction = direction;
        this.thresholdWarning = thresholdWarning;
        this.thresholdCritical = thresholdCritical;
        this.owner = owner;
        this.currentStatus = KriStatus.UNKNOWN;
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
     * 按方向对阈值评定某测量值的状态（核心规则）：
     * UPPER_BAD：value ≥ critical → CRITICAL；value ≥ warning → WARNING；否则 NORMAL。
     * LOWER_BAD：value ≤ critical → CRITICAL；value ≤ warning → WARNING；否则 NORMAL。
     */
    public KriStatus evaluate(BigDecimal value) {
        if (direction == KriDirection.UPPER_BAD) {
            if (value.compareTo(thresholdCritical) >= 0) {
                return KriStatus.CRITICAL;
            }
            if (value.compareTo(thresholdWarning) >= 0) {
                return KriStatus.WARNING;
            }
            return KriStatus.NORMAL;
        } else {
            if (value.compareTo(thresholdCritical) <= 0) {
                return KriStatus.CRITICAL;
            }
            if (value.compareTo(thresholdWarning) <= 0) {
                return KriStatus.WARNING;
            }
            return KriStatus.NORMAL;
        }
    }

    /** 由 Service 在记录测量后回写最近值与最近状态。 */
    void applyCurrent(BigDecimal value, KriStatus status) {
        this.currentValue = value;
        this.currentStatus = status;
    }

    public Long getId() { return id; }
    public Long getOrgId() { return orgId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getUnit() { return unit; }
    public KriDirection getDirection() { return direction; }
    public BigDecimal getThresholdWarning() { return thresholdWarning; }
    public BigDecimal getThresholdCritical() { return thresholdCritical; }
    public BigDecimal getCurrentValue() { return currentValue; }
    public KriStatus getCurrentStatus() { return currentStatus; }
    public String getOwner() { return owner; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
