package com.mandao.grc.modules.regulatory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 年度合规计划项仓储。不写 org 过滤：RLS 自动裁剪可见行。 */
public interface CompliancePlanItemRepository extends JpaRepository<CompliancePlanItem, Long> {

    /** 按序列出某计划的计划项。 */
    List<CompliancePlanItem> findByPlanIdOrderBySeqAsc(Long planId);
}
