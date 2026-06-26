package com.mandao.grc.modules.settings;

import org.springframework.data.jpa.repository.JpaRepository;

/** 品牌配置仓储（平台级单行 id=1，无 RLS）。 */
public interface BrandingConfigRepository extends JpaRepository<BrandingConfig, Long> {
}
