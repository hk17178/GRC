package com.mandao.grc.modules.atv;

import org.springframework.data.jpa.repository.JpaRepository;

/** 威胁库仓储。不写 org 过滤：RLS 自动裁剪可见行。 */
public interface ThreatRepository extends JpaRepository<Threat, Long> {
}
