package com.mandao.grc.modules.audit.management;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * 证据（V44，M3 证据库）。文件 bytea 落库，sha256 指纹上传时固化（防篡改，反向取证依据）。
 *
 * 可挂到 审计计划 / 审计发现 / 整改单 任一对象（至少其一）；org_id 隔离锚点，RLS 裁剪。
 * data 不随 JSON 序列化外发（@JsonIgnore），下载走专用端点。
 */
@Entity
@Table(name = "evidence")
public class Evidence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    @Column(name = "plan_id")
    private Long planId;

    @Column(name = "finding_id")
    private Long findingId;

    @Column(name = "remediation_id")
    private Long remediationId;

    @Column(nullable = false, length = 256)
    private String name;

    @Column(name = "file_name", length = 256)
    private String fileName;

    @Column(name = "content_type", length = 128)
    private String contentType;

    @JsonIgnore
    @Column(nullable = false)
    private byte[] data;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(name = "uploaded_by", length = 64)
    private String uploadedBy;

    @Column(name = "uploaded_at", updatable = false)
    private OffsetDateTime uploadedAt;

    protected Evidence() {
    }

    public Evidence(Long orgId, Long planId, Long findingId, Long remediationId,
                    String name, String fileName, String contentType, byte[] data, String sha256, String uploadedBy) {
        this.orgId = orgId;
        this.planId = planId;
        this.findingId = findingId;
        this.remediationId = remediationId;
        this.name = name;
        this.fileName = fileName;
        this.contentType = contentType;
        this.data = data;
        this.sha256 = sha256;
        this.uploadedBy = uploadedBy;
    }

    @PrePersist
    void onCreate() {
        if (this.uploadedAt == null) {
            this.uploadedAt = OffsetDateTime.now();
        }
    }

    public Long getId() { return id; }
    public Long getOrgId() { return orgId; }
    public Long getPlanId() { return planId; }
    public Long getFindingId() { return findingId; }
    public Long getRemediationId() { return remediationId; }
    public String getName() { return name; }
    public String getFileName() { return fileName; }
    public String getContentType() { return contentType; }
    public byte[] getData() { return data; }
    public String getSha256() { return sha256; }
    public String getUploadedBy() { return uploadedBy; }
    public OffsetDateTime getUploadedAt() { return uploadedAt; }

    /** 文件大小（字节），列表展示用（data 本身不出 JSON）。 */
    public int getSizeBytes() { return data == null ? 0 : data.length; }
}
