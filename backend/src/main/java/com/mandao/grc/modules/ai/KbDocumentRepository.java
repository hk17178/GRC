package com.mandao.grc.modules.ai;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 知识源文档仓库。隔离由 RLS 兜底（查询不手写 org 过滤）。
 */
public interface KbDocumentRepository extends JpaRepository<KbDocument, Long> {

    /** 列出可见范围内的文档，按 id 倒序（最新在前）。 */
    List<KbDocument> findAllByOrderByIdDesc();
}
