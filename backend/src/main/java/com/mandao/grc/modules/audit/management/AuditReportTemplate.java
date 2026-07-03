package com.mandao.grc.modules.audit.management;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * 审计报告模板（V54）：报告正文骨架，生成草稿时可选用（系统组稿附录随后）。
 * org_id 隔离锚点；内置模板种在集团（org 1），子组织可见（可复制正文另存本组织模板）。
 */
@Entity
@Table(name = "audit_report_template")
public class AuditReportTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    @Column(nullable = false, length = 256)
    private String name;

    @Column(length = 64)
    private String category;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    protected AuditReportTemplate() {
    }

    public AuditReportTemplate(Long orgId, String name, String category, String content, String createdBy) {
        this.orgId = orgId;
        this.name = name;
        this.category = category;
        this.content = content;
        this.createdBy = createdBy;
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

    /** 编辑模板（由 Service 校验后调用）。 */
    void applyEdit(String name, String category, String content) {
        if (name != null && !name.isBlank()) {
            this.name = name;
        }
        this.category = category;
        if (content != null && !content.isBlank()) {
            this.content = content;
        }
    }

    void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Long getId() { return id; }
    public Long getOrgId() { return orgId; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public String getContent() { return content; }
    public boolean isEnabled() { return enabled; }
    public String getCreatedBy() { return createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
