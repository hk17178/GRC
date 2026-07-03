package com.mandao.grc.modules.assessment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * 评估范围资产（V48 · R2）：把背景建立的"范围"落到具体资产清单（GB/T 20984 资产识别）。
 *
 * asset_id 软引用 M6 asset（Service 校验可见）；UNIQUE(assessment_id, asset_id) 防重复勾选。
 */
@Entity
@Table(name = "assessment_asset")
public class AssessmentAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    @Column(name = "assessment_id", nullable = false, updatable = false)
    private Long assessmentId;

    @Column(name = "asset_id", nullable = false, updatable = false)
    private Long assetId;

    @Column(name = "added_by", length = 64)
    private String addedBy;

    @Column(name = "added_at", updatable = false)
    private OffsetDateTime addedAt;

    protected AssessmentAsset() {
    }

    public AssessmentAsset(Long orgId, Long assessmentId, Long assetId, String addedBy) {
        this.orgId = orgId;
        this.assessmentId = assessmentId;
        this.assetId = assetId;
        this.addedBy = addedBy;
    }

    @PrePersist
    void onCreate() {
        if (this.addedAt == null) {
            this.addedAt = OffsetDateTime.now();
        }
    }

    public Long getId() { return id; }
    public Long getOrgId() { return orgId; }
    public Long getAssessmentId() { return assessmentId; }
    public Long getAssetId() { return assetId; }
    public String getAddedBy() { return addedBy; }
    public OffsetDateTime getAddedAt() { return addedAt; }
}
