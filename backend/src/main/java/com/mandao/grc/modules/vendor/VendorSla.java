package com.mandao.grc.modules.vendor;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 供应商 SLA 跟踪项（需求 9.2：SLA 项/目标/实际/到期/达标状态）。携 org_id，RLS 裁剪。
 */
@Entity
@Table(name = "vendor_sla")
public class VendorSla {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    @Column(name = "vendor_id", nullable = false)
    private Long vendorId;

    /** SLA 项（如 可用率/到达率/响应时限）。 */
    @Column(nullable = false, length = 128)
    private String item;

    /** 目标（如 ≥99.9%）。 */
    @Column(length = 64)
    private String target;

    /** 实际（如 99.95%）。 */
    @Column(length = 64)
    private String actual;

    /** 到期日期。 */
    @Column(name = "due_date")
    private LocalDate dueDate;

    /** 达标。 */
    @Column(nullable = false)
    private boolean met = true;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    protected VendorSla() {
    }

    public VendorSla(Long orgId, Long vendorId, String item, String target, String actual,
                     LocalDate dueDate, boolean met) {
        this.orgId = orgId;
        this.vendorId = vendorId;
        this.item = item;
        this.target = target;
        this.actual = actual;
        this.dueDate = dueDate;
        this.met = met;
    }

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = OffsetDateTime.now();
        }
    }

    /** 更新实际值与达标状态。 */
    public void track(String actual, boolean met) {
        this.actual = actual;
        this.met = met;
    }

    public Long getId() { return id; }
    public Long getOrgId() { return orgId; }
    public Long getVendorId() { return vendorId; }
    public String getItem() { return item; }
    public String getTarget() { return target; }
    public String getActual() { return actual; }
    public LocalDate getDueDate() { return dueDate; }
    public boolean isMet() { return met; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
