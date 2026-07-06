package com.mandao.grc.modules.ai;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * AI 反馈仓储（B32）。不写 org 过滤：RLS 自动裁剪可见行。
 */
public interface AiFeedbackRepository extends JpaRepository<AiFeedback, Long> {
}
