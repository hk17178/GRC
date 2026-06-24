package com.mandao.grc.modules.assessment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 评估模板项仓储。不写 org 过滤：RLS 自动裁剪可见行。 */
public interface AssessmentTemplateItemRepository extends JpaRepository<AssessmentTemplateItem, Long> {

    /** 按序列出某模板的检查项。 */
    List<AssessmentTemplateItem> findByTemplateIdOrderBySeqAsc(Long templateId);
}
