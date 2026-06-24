package com.mandao.grc.modules.obligation;

import org.springframework.data.jpa.repository.JpaRepository;

/** 合规义务仓储。不写 org 过滤：RLS 自动裁剪可见行。 */
public interface ObligationRepository extends JpaRepository<Obligation, Long> {
}
