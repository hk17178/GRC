package com.mandao.grc.modules.audit.management;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 证据仓储（RLS 按 visible_orgs 裁剪）。 */
public interface EvidenceRepository extends JpaRepository<Evidence, Long> {

    List<Evidence> findAllByOrderByIdDesc();

    List<Evidence> findByPlanIdOrderByIdDesc(Long planId);

    List<Evidence> findByFindingIdOrderByIdDesc(Long findingId);

    List<Evidence> findByRemediationIdOrderByIdDesc(Long remediationId);
}
