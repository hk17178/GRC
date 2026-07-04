package com.mandao.grc.modules.assessment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 评估计划仓储（RLS 裁剪）。 */
public interface AssessmentPlanRepository extends JpaRepository<AssessmentPlan, Long> {

    /** 悬挂引用清理（八轮 8-11/A31：评估删除/作废时同步计划）。 */
    java.util.List<AssessmentPlan> findByAssessmentId(Long assessmentId);

    List<AssessmentPlan> findAllByOrderByPlannedDateAsc();
}
