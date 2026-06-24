package com.mandao.grc.modules.regulatory;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 监管问询仓储。RLS 自动按 visible_orgs 裁剪，无手写 org 过滤。
 */
public interface RegInquiryRepository extends JpaRepository<RegInquiry, Long> {
}
