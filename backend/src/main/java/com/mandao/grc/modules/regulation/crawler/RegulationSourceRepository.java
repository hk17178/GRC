package com.mandao.grc.modules.regulation.crawler;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 追踪源仓储（查询自动受 RLS 裁剪）。 */
public interface RegulationSourceRepository extends JpaRepository<RegulationSource, Long> {

    List<RegulationSource> findAllByOrderByIdDesc();

    List<RegulationSource> findByEnabledTrue();
}
