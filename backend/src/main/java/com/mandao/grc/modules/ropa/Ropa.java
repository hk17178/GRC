package com.mandao.grc.modules.ropa;

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
 * 个人信息处理活动（ROPA），映射 V9 ropa 表（M6）。
 *
 * 隔离锚点 org_id；可见/可写由 RLS 按 app.visible_orgs 自动裁剪（V9 已建 USING/WITH CHECK）。
 * 主键 BIGSERIAL，故用 {@link GenerationType#IDENTITY}。
 *
 * 状态机：DRAFT → ACTIVE → RETIRED（见 {@link RopaService}）。
 *
 * 设计依据：需求文档 M6（个人信息处理活动 ROPA）、D1-2、D2-5。
 */
@Entity
@Table(name = "ropa")
public class Ropa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 隔离锚点：所属组织。 */
    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    /** 处理活动名称。 */
    @Column(name = "activity_name", nullable = false, length = 256)
    private String activityName;

    /** 处理目的。 */
    @Column(length = 512)
    private String purpose;

    /** 涉及的个人信息类别（自由文本）。 */
    @Column(name = "data_categories", columnDefinition = "TEXT")
    private String dataCategories;

    /** 合法性基础（同意/合同/法定义务等）。 */
    @Column(name = "legal_basis", length = 32)
    private String legalBasis;

    /** 是否涉及跨境传输。 */
    @Column(name = "cross_border", nullable = false)
    private boolean crossBorder;

    /** 留存期限。 */
    @Column(length = 64)
    private String retention;

    /** 处理方式（合规扩展包 B14：个保法§55/56 法定字段——收集/存储/使用/加工/传输/提供/公开/删除等）。 */
    @Column(name = "processing_method", length = 256)
    private String processingMethod;

    /** 接收方（B14：数据接收/共享的第三方，个保法要求告知与记录）。 */
    @Column(columnDefinition = "TEXT")
    private String recipients;

    /** ROPA 生命周期状态机当前态。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RopaStatus status = RopaStatus.DRAFT;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    /** JPA 要求的无参构造。 */
    protected Ropa() {
    }

    /** 业务构造：以 DRAFT 态登记处理活动。 */
    public Ropa(Long orgId, String activityName, String purpose, String dataCategories,
                String legalBasis, boolean crossBorder, String retention) {
        this(orgId, activityName, purpose, dataCategories, legalBasis, crossBorder, retention, null, null);
    }

    /** 业务构造（B14 全字段：含处理方式/接收方）。 */
    public Ropa(Long orgId, String activityName, String purpose, String dataCategories,
                String legalBasis, boolean crossBorder, String retention,
                String processingMethod, String recipients) {
        this.orgId = orgId;
        this.activityName = activityName;
        this.purpose = purpose;
        this.dataCategories = dataCategories;
        this.legalBasis = legalBasis;
        this.crossBorder = crossBorder;
        this.retention = retention;
        this.processingMethod = processingMethod;
        this.recipients = recipients;
        this.status = RopaStatus.DRAFT;
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
    public String getActivityName() { return activityName; }
    public String getPurpose() { return purpose; }
    public String getDataCategories() { return dataCategories; }
    public String getLegalBasis() { return legalBasis; }
    public boolean isCrossBorder() { return crossBorder; }
    public String getRetention() { return retention; }
    public String getProcessingMethod() { return processingMethod; }
    public String getRecipients() { return recipients; }
    public RopaStatus getStatus() { return status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    /** 包级可见，仅由 Service 在校验后调用，封装状态变更。 */
    void setStatus(RopaStatus status) { this.status = status; }
}
