package com.mandao.grc.modules.audit.management;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 年度审计计划条目仓储（RLS 裁剪）。 */
public interface AuditAnnualItemRepository extends JpaRepository<AuditAnnualItem, Long> {

    /** 按风险排序（1 最高在前）再按季度列出。 */
    List<AuditAnnualItem> findByAnnualIdOrderByRiskRankAscQuarterAsc(Long annualId);
}
