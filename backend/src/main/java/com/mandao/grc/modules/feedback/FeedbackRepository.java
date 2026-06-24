package com.mandao.grc.modules.feedback;

import org.springframework.data.jpa.repository.JpaRepository;

/** 反馈仓储。不写 org 过滤：RLS 自动裁剪可见行。 */
public interface FeedbackRepository extends JpaRepository<Feedback, Long> {
}
