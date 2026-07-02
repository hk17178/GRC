package com.mandao.grc.modules.policy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * 制度版本历史快照（需求 3.2.1 版本时间线）。
 *
 * 修订（{@link PolicyService#revise}）时把旧版标题/正文归档到此表，支持版本时间线与回看。
 * 携 org_id 隔离锚点，RLS 裁剪。
 */
@Entity
@Table(name = "policy_version")
public class PolicyVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    @Column(name = "policy_id", nullable = false)
    private Long policyId;

    /** 被归档的版本号。 */
    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @Column(length = 256)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    /** 修订说明（新版本为什么改）。 */
    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "changed_by", length = 64)
    private String changedBy;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    protected PolicyVersion() {
    }

    public PolicyVersion(Long orgId, Long policyId, Integer versionNo, String title, String content,
                         String note, String changedBy) {
        this.orgId = orgId;
        this.policyId = policyId;
        this.versionNo = versionNo;
        this.title = title;
        this.content = content;
        this.note = note;
        this.changedBy = changedBy;
    }

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = OffsetDateTime.now();
        }
    }

    public Long getId() { return id; }
    public Long getOrgId() { return orgId; }
    public Long getPolicyId() { return policyId; }
    public Integer getVersionNo() { return versionNo; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public String getNote() { return note; }
    public String getChangedBy() { return changedBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
