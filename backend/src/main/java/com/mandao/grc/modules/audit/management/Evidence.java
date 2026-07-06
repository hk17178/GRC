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

    /** 关联监管报送（七轮 7-2：回执证据挂报送事项）。 */
    @Column(name = "filing_id")
    private Long filingId;

    /** 关联重大事件（七轮 7-2：报送回执/监管确认材料挂重大事件）。 */
    @Column(name = "incident_id")
    private Long incidentId;

    /** 关联监管问询（M11 B13：答复回函/监管确认材料挂问询）。 */
    @Column(name = "inquiry_id")
    private Long inquiryId;

    /** 关联处罚约谈（M11 B13：整改证明/缴款凭证挂处罚）。 */
    @Column(name = "penalty_id")
    private Long penaltyId;

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
    public Long getFilingId() { return filingId; }
    public Long getIncidentId() { return incidentId; }
    public Long getInquiryId() { return inquiryId; }
    public Long getPenaltyId() { return penaltyId; }

    /** 挂接报送/重大事件/问询/处罚（七轮 7-2 + M11 B13，由 Service 在上传时按参数调用）。 */
    void attachRegulatory(Long filingId, Long incidentId, Long inquiryId, Long penaltyId) {
        this.filingId = filingId;
        this.incidentId = incidentId;
        this.inquiryId = inquiryId;
        this.penaltyId = penaltyId;
    }
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
