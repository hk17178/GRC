package com.mandao.grc.modules.assessment;

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
 * 评估过程文档（V51 · R3）：GB/T 20984 过程文档管理。
 *
 * UPLOAD=人工上传件（计划书/访谈记录等，bytea+sha256 固化）；
 * SYSTEM=系统生成件登记（报告/RTP 等由端点即时生成，登记留痕不存字节）。
 * data 不随 JSON 外发，下载走专用端点。
 */
@Entity
@Table(name = "assessment_doc")
public class AssessmentDoc {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    @Column(name = "assessment_id", nullable = false, updatable = false)
    private Long assessmentId;

    /** PLAN/INTERVIEW/REPORT/RTP/ACCEPTANCE/OTHER。 */
    @Column(name = "doc_type", nullable = false, length = 16)
    private String docType;

    @Column(nullable = false, length = 256)
    private String name;

    @Column(name = "file_name", length = 256)
    private String fileName;

    @Column(name = "content_type", length = 128)
    private String contentType;

    @JsonIgnore
    @Column
    private byte[] data;

    /** UPLOAD / SYSTEM。 */
    @Column(nullable = false, length = 8)
    private String source = "UPLOAD";

    @Column(length = 64)
    private String sha256;

    @Column(name = "uploaded_by", length = 64)
    private String uploadedBy;

    @Column(name = "uploaded_at", updatable = false)
    private OffsetDateTime uploadedAt;

    protected AssessmentDoc() {
    }

    public AssessmentDoc(Long orgId, Long assessmentId, String docType, String name,
                         String fileName, String contentType, byte[] data, String sha256, String uploadedBy) {
        this.orgId = orgId;
        this.assessmentId = assessmentId;
        this.docType = docType;
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
    public Long getAssessmentId() { return assessmentId; }
    public String getDocType() { return docType; }
    public String getName() { return name; }
    public String getFileName() { return fileName; }
    public String getContentType() { return contentType; }
    public byte[] getData() { return data; }
    public String getSource() { return source; }
    public String getSha256() { return sha256; }
    public String getUploadedBy() { return uploadedBy; }
    public OffsetDateTime getUploadedAt() { return uploadedAt; }

    /** 文件大小（字节），列表展示用。 */
    public int getSizeBytes() { return data == null ? 0 : data.length; }
}
