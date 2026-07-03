package com.mandao.grc.modules.assessment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** 评估范围资产仓储（RLS 按 visible_orgs 裁剪）。 */
public interface AssessmentAssetRepository extends JpaRepository<AssessmentAsset, Long> {

    List<AssessmentAsset> findByAssessmentIdOrderByIdAsc(Long assessmentId);

    Optional<AssessmentAsset> findByAssessmentIdAndAssetId(Long assessmentId, Long assetId);
}
