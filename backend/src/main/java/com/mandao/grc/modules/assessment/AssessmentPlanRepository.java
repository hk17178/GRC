package com.mandao.grc.modules.assessment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 评估计划仓储（RLS 裁剪）。 */
public interface AssessmentPlanRepository extends JpaRepository<AssessmentPlan, Long> {

    List<AssessmentPlan> findAllByOrderByPlannedDateAsc();
}
