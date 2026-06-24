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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 处罚约谈台账（M11 监管事项），映射 V8 reg_penalty 表。
 *
 * 隔离锚点 org_id；可见/可写由 RLS 按 app.visible_orgs 自动裁剪（V8 已建 USING/WITH CHECK）。
 * 状态机：OPEN → RECTIFYING → CLOSED（见 {@link RegPenaltyService}）。
 *
 * 设计依据：需求文档 M11、D1-2 §23、D2-5。
 */
@Entity
@Table(name = "reg_penalty")
public class RegPenalty {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    @Column(length = 256)
    private String title;

    @Column(length = 64)
    private String regulator;

    /** 处罚类型（罚款/警告/约谈等）。 */
    @Column(name = "penalty_type", length = 32)
    private String penaltyType;

    /** 罚没金额（可空）。 */
    @Column
    private BigDecimal amount;

    /** 发生日。 */
    @Column(name = "occurred_date")
    private LocalDate occurredDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RegPenaltyStatus status = RegPenaltyStatus.OPEN;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    protected RegPenalty() {
    }

    /** 业务构造：以 OPEN 态新建处罚/约谈记录。 */
    public RegPenalty(Long orgId, String title, String regulator, String penaltyType,
                      BigDecimal amount, LocalDate occurredDate) {
        this.orgId = orgId;
        this.title = title;
        this.regulator = regulator;
        this.penaltyType = penaltyType;
        this.amount = amount;
        this.occurredDate = occurredDate;
        this.status = RegPenaltyStatus.OPEN;
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
    public String getPenaltyType() { return penaltyType; }
    public BigDecimal getAmount() { return amount; }
    public LocalDate getOccurredDate() { return occurredDate; }
    public RegPenaltyStatus getStatus() { return status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    void setStatus(RegPenaltyStatus status) { this.status = status; }
}
