package com.mandao.grc.modules.audit.management;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 审计报告模板仓储（RLS 裁剪）。 */
public interface AuditReportTemplateRepository extends JpaRepository<AuditReportTemplate, Long> {

    List<AuditReportTemplate> findAllByOrderByIdAsc();
}
