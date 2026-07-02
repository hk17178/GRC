package com.mandao.grc.modules.regulation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 法规-制度映射仓储（RLS 裁剪）。 */
public interface RegulationPolicyMapRepository extends JpaRepository<RegulationPolicyMap, Long> {

    List<RegulationPolicyMap> findByRegulationId(Long regulationId);

    List<RegulationPolicyMap> findByPolicyId(Long policyId);
}
