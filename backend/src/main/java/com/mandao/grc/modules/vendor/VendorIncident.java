package com.mandao.grc.modules.vendor;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * 供应商外部事件（事件触发复评，需求 9.2/9.3.2）。
 *
 * 生命周期：OPEN（登记）→ REASSESSING（已触发复评）→ CLOSED（复评闭环）。
 * 携 org_id，RLS 裁剪。
 */
@Entity
@Table(name = "vendor_incident")
public class VendorIncident {

    public static final String OPEN = "OPEN";
    public static final String REASSESSING = "REASSESSING";
    public static final String CLOSED = "CLOSED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    @Column(name = "vendor_id", nullable = false)
    private Long vendorId;

    /** 事件（如 被曝数据泄露）。 */
    @Column(nullable = false, length = 256)
    private String event;

    /** 来源（媒体/监管通报/客户投诉）。 */
    @Column(length = 128)
    private String source;

    /** 事件风险等级（五级字符串）。 */
    @Column(name = "risk_level", length = 16)
    private String riskLevel;

    @Column(nullable = false, length = 16)
    private String status = OPEN;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    protected VendorIncident() {
    }

    public VendorIncident(Long orgId, Long vendorId, String event, String source, String riskLevel) {
        this.orgId = orgId;
        this.vendorId = vendorId;
        this.event = event;
        this.source = source;
        this.riskLevel = riskLevel;
    }

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = OffsetDateTime.now();
        }
    }

    public void markReassessing() {
        this.status = REASSESSING;
    }

    public void close() {
        this.status = CLOSED;
        this.closedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public Long getOrgId() { return orgId; }
    public Long getVendorId() { return vendorId; }
    public String getEvent() { return event; }
    public String getSource() { return source; }
    public String getRiskLevel() { return riskLevel; }
    public String getStatus() { return status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getClosedAt() { return closedAt; }
}
