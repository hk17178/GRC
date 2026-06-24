package com.mandao.grc.modules.assessment;

import org.springframework.data.jpa.repository.JpaRepository;

/** 评估模板仓储。不写 org 过滤：RLS 自动裁剪可见行。 */
public interface AssessmentTemplateRepository extends JpaRepository<AssessmentTemplate, Long> {
}
