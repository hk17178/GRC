package com.mandao.grc.modules.audit.management;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 年度审计计划仓储（RLS 裁剪）。 */
public interface AuditAnnualPlanRepository extends JpaRepository<AuditAnnualPlan, Long> {

    List<AuditAnnualPlan> findAllByOrderByYearDesc();
}
