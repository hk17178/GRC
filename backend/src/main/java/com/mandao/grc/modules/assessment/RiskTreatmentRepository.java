package com.mandao.grc.modules.assessment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** 风险处置计划仓储（RLS 裁剪；一发现一计划）。 */
public interface RiskTreatmentRepository extends JpaRepository<RiskTreatment, Long> {

    Optional<RiskTreatment> findByFindingId(Long findingId);

    List<RiskTreatment> findByFindingIdIn(List<Long> findingIds);
}
