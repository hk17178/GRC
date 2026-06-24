package com.mandao.grc.modules.regulation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 法规变更仓储。不写 org 过滤：RLS 自动裁剪可见行。 */
public interface RegulationChangeRepository extends JpaRepository<RegulationChange, Long> {

    /** 列出某法规的全部变更动态（新→旧）。 */
    List<RegulationChange> findByRegulationIdOrderByIdDesc(Long regulationId);
}
