package com.mandao.grc.modules.vendor;

import com.mandao.grc.modules.assessment.RiskLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * 供应商风险评估记录（准入/年度/监测评估）。
 *
 * 携带 org_id 隔离锚点（与供应商同组织）；可见性/可写性由 RLS 自动裁剪。
 * 至少一次评估是供应商启用（准入门控）的前提。
 */
@Entity
@Table(name = "vendor_assessment")
public class VendorAssessment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 隔离锚点：所属组织。 */
    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    /** 所评估的供应商。 */
    @Column(name = "vendor_id", nullable = false, updatable = false)
    private Long vendorId;

    /** 评估风险等级（平台五级）。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 12)
    private RiskLevel riskLevel;

    /** 评估得分（0–100，可空）。 */
    @Column
    private Integer score;

    /** 评估人。 */
    @Column(length = 64)
    private String assessor;

    /** 评估结论。 */
    @Column(columnDefinition = "TEXT")
    private String conclusion;

    /** 评估类型：ONBOARDING 首次准入 / ANNUAL 年度定期 / RENEWAL 续约 / EVENT 事件复评。 */
    @Column(name = "assess_type", nullable = false, length = 16)
    private String assessType = "ONBOARDING";

    @Column(name = "assessed_at", updatable = false)
    private OffsetDateTime assessedAt;

    /** 评估依据：所用评估表单/标准/维度说明（过程可溯）。 */
    @Column(columnDefinition = "TEXT")
    private String basis;

    /** 评估表单/报告原件（过程文档留存 + sha256 固化）。 */
    @Column(name = "doc_name", length = 255)
    private String docName;

    @Column(name = "doc_sha256", length = 64)
    private String docSha256;

    @Column(name = "doc_bytes")
    private byte[] docBytes;

    /** JPA 要求的无参构造。 */
    protected VendorAssessment() {
    }

    /** 业务构造：登记一次供应商评估。 */
    public VendorAssessment(Long orgId, Long vendorId, RiskLevel riskLevel, Integer score,
                            String assessor, String conclusion, String assessType) {
        this(orgId, vendorId, riskLevel, score, assessor, conclusion, assessType, null);
    }

    /** 业务构造（带评估依据）。 */
    public VendorAssessment(Long orgId, Long vendorId, RiskLevel riskLevel, Integer score,
                            String assessor, String conclusion, String assessType, String basis) {
        this.assessType = assessType == null ? "ONBOARDING" : assessType;
        this.basis = basis;
        // 委托旧构造逻辑：
        init(orgId, vendorId, riskLevel, score, assessor, conclusion);
    }

    private void init(Long orgId, Long vendorId, RiskLevel riskLevel, Integer score,
                      String assessor, String conclusion) {
        this.orgId = orgId;
        this.vendorId = vendorId;
        this.riskLevel = riskLevel;
        this.score = score;
        this.assessor = assessor;
        this.conclusion = conclusion;
    }

    @PrePersist
    void onCreate() {
        if (this.assessedAt == null) {
            this.assessedAt = OffsetDateTime.now();
        }
    }

    public Long getId() { return id; }
    public Long getOrgId() { return orgId; }
    public Long getVendorId() { return vendorId; }
    public RiskLevel getRiskLevel() { return riskLevel; }
    public String getAssessType() { return assessType; }
    public Integer getScore() { return score; }
    public String getAssessor() { return assessor; }
    public String getConclusion() { return conclusion; }
    public OffsetDateTime getAssessedAt() { return assessedAt; }
    public String getBasis() { return basis; }
    public String getDocName() { return docName; }
    public String getDocSha256() { return docSha256; }
    public byte[] getDocBytes() { return docBytes; }

    /** 挂载评估表单/报告原件（过程文档留存）。 */
    void attachDocument(String docName, String docSha256, byte[] docBytes) {
        this.docName = docName;
        this.docSha256 = docSha256;
        this.docBytes = docBytes;
    }
}
