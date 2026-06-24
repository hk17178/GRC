package com.mandao.grc.modules.assessment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 评估项仓储。不写 org 过滤：RLS 自动裁剪可见行。 */
public interface AssessmentItemRepository extends JpaRepository<AssessmentItem, Long> {

    /** 按序列出某评估的评估项。 */
    List<AssessmentItem> findByAssessmentIdOrderBySeqAsc(Long assessmentId);
}
