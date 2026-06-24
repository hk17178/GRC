package com.mandao.grc.modules.kri;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * KRI 测量记录（指标的时序观测点，支撑"持续监测"与趋势）。
 *
 * 携带 org_id 隔离锚点（与所属 KRI 同组织）；可见性/可写性由 RLS 自动裁剪。
 * status 为记录该测量当时按阈值评定的状态，便于回溯历史告警。
 */
@Entity
@Table(name = "kri_measurement")
public class KriMeasurement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 隔离锚点：所属组织。 */
    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    /** 所属 KRI。 */
    @Column(name = "kri_id", nullable = false, updatable = false)
    private Long kriId;

    /** 测量值。 */
    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal value;

    /** 测量当时评定的状态。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private KriStatus status;

    @Column(name = "measured_at", updatable = false)
    private OffsetDateTime measuredAt;

    /** 备注（数据来源/说明，可空）。 */
    @Column(columnDefinition = "TEXT")
    private String note;

    /** JPA 要求的无参构造。 */
    protected KriMeasurement() {
    }

    /** 业务构造：登记一条测量（status 由 Service 用所属 KRI 评定后传入）。 */
    public KriMeasurement(Long orgId, Long kriId, BigDecimal value, KriStatus status, String note) {
        this.orgId = orgId;
        this.kriId = kriId;
        this.value = value;
        this.status = status;
        this.note = note;
    }

    @PrePersist
    void onCreate() {
        if (this.measuredAt == null) {
            this.measuredAt = OffsetDateTime.now();
        }
    }

    public Long getId() { return id; }
    public Long getOrgId() { return orgId; }
    public Long getKriId() { return kriId; }
    public BigDecimal getValue() { return value; }
    public KriStatus getStatus() { return status; }
    public OffsetDateTime getMeasuredAt() { return measuredAt; }
    public String getNote() { return note; }
}
