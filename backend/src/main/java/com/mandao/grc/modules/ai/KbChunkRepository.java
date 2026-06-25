package com.mandao.grc.modules.ai;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 文档切块仓库。隔离由 RLS 兜底。
 */
public interface KbChunkRepository extends JpaRepository<KbChunk, Long> {

    /** 列出某文档的切块，按序号升序。 */
    List<KbChunk> findByDocumentIdOrderBySeqAsc(Long documentId);
}
