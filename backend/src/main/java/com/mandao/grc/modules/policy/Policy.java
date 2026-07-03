package com.mandao.grc.modules.policy;

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
 * 制度（M1 制度体系业务实体）。
 *
 * 携带 org_id 隔离锚点；其可见性与可写性由 RLS 依据会话变量 app.visible_orgs 自动裁剪，
 * 应用代码无需手写 org 过滤（隔离由 {@link com.mandao.grc.common.isolation.OrgScopeAspect} 切面 + RLS 兜底）。
 *
 * 主键为数据库 BIGSERIAL 自增，故用 {@link GenerationType#IDENTITY}。
 */
@Entity
@Table(name = "policy")
public class Policy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 隔离锚点：所属组织。新建时由 Service 依据当前可见上下文设置。 */
    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    /** 制度编号（org 内唯一，对应表上的 UNIQUE(org_id, code)）。 */
    @Column(length = 64)
    private String code;

    /** 制度标题。 */
    @Column(length = 256)
    private String title;

    /** 制度正文。 */
    @Column(columnDefinition = "TEXT")
    private String content;

    /** 状态机当前态，以字符串持久化（与 DB 的 VARCHAR 一致，便于排查）。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private PolicyStatus status = PolicyStatus.DRAFT;

    /** 版本号，从 1 起。 */
    @Column(nullable = false)
    private Integer version = 1;

    // ---- 元数据（需求 3.2.1：分类/生效日期/复审周期/责任部门/责任人）----

    /** 体系分类（ISO27001/MLPS/PIPL/PBOC/PCI_DSS/GENERAL）。 */
    @Column(length = 16)
    private String framework;

    /** 生效日期。 */
    @Column(name = "effective_date")
    private java.time.LocalDate effectiveDate;

    /** 复审周期（月）。 */
    @Column(name = "review_cycle_months")
    private Integer reviewCycleMonths;

    /** 责任部门。 */
    @Column(name = "owner_dept", length = 64)
    private String ownerDept;

    /** 责任人。 */
    @Column(length = 64)
    private String owner;

    // ---- 制度原件（六轮 #6：docx 上传，POI 提取全文写 content，原件 sha256 固化留档）----

    /** 原件文件名。 */
    @Column(name = "doc_name", length = 256)
    private String docName;

    /** 原件 sha256（固化防篡改，与证据库同款做法）。 */
    @Column(name = "doc_sha256", length = 64)
    private String docSha256;

    /** 原件字节（docx）。 */
    @Column(name = "doc_bytes")
    private byte[] docBytes;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    /** JPA 要求的无参构造。 */
    protected Policy() {
    }

    /**
     * 业务构造：以草稿态新建一项制度。
     * 时间戳由 {@link #onCreate()} 在落库前补齐。
     */
    public Policy(Long orgId, String code, String title, String content) {
        this.orgId = orgId;
        this.code = code;
        this.title = title;
        this.content = content;
        this.status = PolicyStatus.DRAFT;
        this.version = 1;
    }

    /** 落库前补齐创建/更新时间。 */
    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        this.updatedAt = now;
    }

    /** 更新前刷新更新时间。 */
    @PreUpdate
    void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getOrgId() {
        return orgId;
    }

    public String getCode() {
        return code;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public PolicyStatus getStatus() {
        return status;
    }

    public Integer getVersion() {
        return version;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public String getFramework() { return framework; }
    public java.time.LocalDate getEffectiveDate() { return effectiveDate; }
    public Integer getReviewCycleMonths() { return reviewCycleMonths; }
    public String getOwnerDept() { return ownerDept; }
    public String getOwner() { return owner; }

    /** 更新元数据（由 Service 调用）。 */
    void updateMeta(String framework, java.time.LocalDate effectiveDate, Integer reviewCycleMonths,
                    String ownerDept, String owner) {
        this.framework = framework;
        this.effectiveDate = effectiveDate;
        this.reviewCycleMonths = reviewCycleMonths;
        this.ownerDept = ownerDept;
        this.owner = owner;
    }

    /** 修订：换入新标题/正文并把版本号 +1（旧版快照由 Service 先行存档）。 */
    void reviseTo(String title, String content) {
        this.title = title;
        this.content = content;
        this.version = this.version + 1;
    }

    /** 由 Service 在校验合法流转后调用，推进状态机。 */
    void setStatus(PolicyStatus status) {
        this.status = status;
    }

    public String getDocName() { return docName; }
    public String getDocSha256() { return docSha256; }

    /** 原件字节不随 JSON 序列化外泄（下载走专用端点）。 */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public byte[] getDocBytes() { return docBytes; }

    /** 挂载制度原件（由 Service 在提取全文后调用）：换文即换全文。 */
    void attachDocument(String docName, String docSha256, byte[] docBytes, String extractedText) {
        this.docName = docName;
        this.docSha256 = docSha256;
        this.docBytes = docBytes;
        this.content = extractedText;
    }
}
