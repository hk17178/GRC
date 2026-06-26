package com.mandao.grc.modules.assessment.form;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** 评估填写仓储（查询自动受 RLS 裁剪）。 */
public interface AssessmentAnswerRepository extends JpaRepository<AssessmentAnswer, Long> {

    /** 取某评估的填写（一个评估一份）。 */
    Optional<AssessmentAnswer> findByAssessmentId(Long assessmentId);
}
