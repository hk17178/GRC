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

    /** 由 Service 在校验合法流转后调用，推进状态机。 */
    void setStatus(PolicyStatus status) {
        this.status = status;
    }
}
