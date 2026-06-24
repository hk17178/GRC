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
 * 监管问询台账（M11 监管事项），映射 V8 reg_inquiry 表。
 *
 * 隔离锚点 org_id；可见/可写由 RLS 按 app.visible_orgs 自动裁剪（V8 已建 USING/WITH CHECK）。
 * 状态机：OPEN → RESPONDING → CLOSED（见 {@link RegInquiryService}）。
 *
 * 设计依据：需求文档 M11、D1-2 §23、D2-5。
 */
@Entity
@Table(name = "reg_inquiry")
public class RegInquiry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    @Column(length = 256)
    private String title;

    @Column(length = 64)
    private String regulator;

    /** 收到问询日。 */
    @Column(name = "received_date")
    private LocalDate receivedDate;

    /** 答复截止日。 */
    @Column(name = "due_date")
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RegInquiryStatus status = RegInquiryStatus.OPEN;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    protected RegInquiry() {
    }

    /** 业务构造：以 OPEN 态新建监管问询。 */
    public RegInquiry(Long orgId, String title, String regulator, LocalDate receivedDate, LocalDate dueDate) {
        this.orgId = orgId;
        this.title = title;
        this.regulator = regulator;
        this.receivedDate = receivedDate;
        this.dueDate = dueDate;
        this.status = RegInquiryStatus.OPEN;
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
    public LocalDate getReceivedDate() { return receivedDate; }
    public LocalDate getDueDate() { return dueDate; }
    public RegInquiryStatus getStatus() { return status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    void setStatus(RegInquiryStatus status) { this.status = status; }
}
