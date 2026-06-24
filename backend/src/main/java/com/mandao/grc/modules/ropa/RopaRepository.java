package com.mandao.grc.modules.ropa;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 个人信息处理活动（ROPA）仓储。RLS 自动按 visible_orgs 裁剪，无手写 org 过滤
 * （与 AssessmentRepository/RegFilingRepository 同范式）。
 */
public interface RopaRepository extends JpaRepository<Ropa, Long> {
}
