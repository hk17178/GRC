package com.mandao.grc.modules.assessment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 评估过程文档仓储（RLS 裁剪）。 */
public interface AssessmentDocRepository extends JpaRepository<AssessmentDoc, Long> {

    List<AssessmentDoc> findByAssessmentIdOrderByIdDesc(Long assessmentId);
}
