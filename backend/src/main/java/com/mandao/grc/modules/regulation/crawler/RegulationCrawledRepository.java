package com.mandao.grc.modules.regulation.crawler;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 采集法规仓储（查询自动受 RLS 裁剪）。 */
public interface RegulationCrawledRepository extends JpaRepository<RegulationCrawled, Long> {

    /** 当前组织是否已有该去重键（用于增量去重）。 */
    boolean existsByDedupKey(String dedupKey);

    /** 全部采集法规（按采集时间倒序）。 */
    List<RegulationCrawled> findAllByOrderByFetchedAtDesc();

    /** 某源的采集法规。 */
    List<RegulationCrawled> findBySourceIdOrderByFetchedAtDesc(Long sourceId);
}
