package com.mandao.grc.modules.ai;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** 场景模型路由仓储（平台级，无 RLS）。 */
public interface AiModelRouteRepository extends JpaRepository<AiModelRoute, Long> {

    Optional<AiModelRoute> findByScenario(String scenario);
}
