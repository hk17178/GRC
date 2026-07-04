package com.mandao.grc.modules.atv;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * 威胁（威胁库，A-T-V 风险识别的"威胁"维）。
 *
 * 携带 org_id 隔离锚点；可见性/可写性由 RLS 依据 app.visible_orgs 自动裁剪。
 */
@Entity
@Table(name = "threat")
public class Threat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 隔离锚点：所属组织。 */
    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    /** 威胁编码（组织内唯一）。 */
    @Column(nullable = false, length = 32)
    private String code;

    /** 威胁名称。 */
    @Column(nullable = false, length = 128)
    private String name;

    /** 威胁分类（如 恶意代码、越权、物理、自然）。 */
    @Column(length = 64)
    private String category;

    /** 描述。 */
    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    /** JPA 要求的无参构造。 */
    protected Threat() {
    }

    /** 业务构造：登记一个威胁。 */
    public Threat(Long orgId, String code, String name, String category, String description) {
        this.orgId = orgId;
        this.code = code;
        this.name = name;
        this.category = category;
        this.description = description;
    }

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = OffsetDateTime.now();
        }
    }

    public Long getId() { return id; }
    public Long getOrgId() { return orgId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public String getDescription() { return description; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    /** 更新条目（八轮 8-10：三库可维护——名称/分类/说明可改，编码不可改保引用稳定）。 */
    void update(String name, String category, String description) {
        this.name = name;
        this.category = category;
        this.description = description;
    }
}
