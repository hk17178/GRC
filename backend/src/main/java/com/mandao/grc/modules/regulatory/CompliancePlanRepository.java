package com.mandao.grc.modules.regulatory;

import org.springframework.data.jpa.repository.JpaRepository;

/** 年度合规计划仓储。不写 org 过滤：RLS 自动裁剪可见行。 */
public interface CompliancePlanRepository extends JpaRepository<CompliancePlan, Long> {
}
