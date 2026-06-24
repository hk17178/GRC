package com.mandao.grc.modules.regulation;

import org.springframework.data.jpa.repository.JpaRepository;

/** 法规库仓储。不写 org 过滤：RLS 自动裁剪可见行。 */
public interface RegulationRepository extends JpaRepository<Regulation, Long> {
}
