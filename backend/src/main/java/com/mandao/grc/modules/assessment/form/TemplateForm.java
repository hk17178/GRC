package com.mandao.grc.modules.assessment.form;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * 表单模板（挂在 assessment_template 下的某一版"上传 docx + 解析 schema"）。
 *
 * 携带 org_id 隔离锚点，RLS 按 visible_orgs 裁剪。一个评估模板可有多版本表单，
 * 同模板仅一条 ACTIVE（DB 部分唯一索引保证）。docx 原件留作 P3 回填导出官方格式报告。
 */
@Entity
@Table(name = "template_form")
public class TemplateForm {

    /** 草稿/生效/停用。 */
    public static final String DRAFT = "DRAFT";
    public static final String ACTIVE = "ACTIVE";
    public static final String RETIRED = "RETIRED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    /** 所属评估模板。 */
    @Column(name = "template_id", nullable = false)
    private Long templateId;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo = 1;

    @Column(length = 128)
    private String name;

    /** 上传的 .docx 原件（bytea）；P3 回填导出用。 */
    @Column(name = "docx")
    private byte[] docx;

    /** 解析出的表单结构（FormSchema 的 JSON 文本）。 */
    @Column(name = "schema_json", nullable = false, columnDefinition = "TEXT")
    private String schemaJson;

    @Column(nullable = false, length = 16)
    private String status = DRAFT;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    protected TemplateForm() {
    }

    public TemplateForm(Long orgId, Long templateId, Integer versionNo, String name,
                        byte[] docx, String schemaJson) {
        this.orgId = orgId;
        this.templateId = templateId;
        this.versionNo = versionNo;
        this.name = name;
        this.docx = docx;
        this.schemaJson = schemaJson;
        this.status = DRAFT;
    }

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = OffsetDateTime.now();
        }
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getId() { return id; }
    public Long getOrgId() { return orgId; }
    public Long getTemplateId() { return templateId; }
    public Integer getVersionNo() { return versionNo; }
    public String getName() { return name; }
    public byte[] getDocx() { return docx; }
    public String getSchemaJson() { return schemaJson; }
    public String getStatus() { return status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
