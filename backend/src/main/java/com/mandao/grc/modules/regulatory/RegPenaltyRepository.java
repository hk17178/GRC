package com.mandao.grc.modules.regulatory;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 处罚约谈仓储。RLS 自动按 visible_orgs 裁剪，无手写 org 过滤。
 */
public interface RegPenaltyRepository extends JpaRepository<RegPenalty, Long> {
}
