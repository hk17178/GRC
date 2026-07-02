package com.mandao.grc.modules.ai;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** AI 治理条目仓储（平台级，无 RLS）。 */
public interface AiGovernanceRepository extends JpaRepository<AiGovernance, Long> {

    List<AiGovernance> findByKindOrderByIdAsc(String kind);

    List<AiGovernance> findByKindAndEnabledTrue(String kind);
}
