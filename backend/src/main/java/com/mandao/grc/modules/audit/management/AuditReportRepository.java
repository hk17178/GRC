package com.mandao.grc.modules.audit.management;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** 审计报告仓储（RLS 按 visible_orgs 裁剪；一计划一报告）。 */
public interface AuditReportRepository extends JpaRepository<AuditReport, Long> {

    Optional<AuditReport> findByPlanId(Long planId);
}
