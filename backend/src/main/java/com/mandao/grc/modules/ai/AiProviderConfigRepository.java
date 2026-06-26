package com.mandao.grc.modules.ai;

import org.springframework.data.jpa.repository.JpaRepository;

/** 大模型接入配置仓储（平台级单行 id=1，无 RLS）。 */
public interface AiProviderConfigRepository extends JpaRepository<AiProviderConfig, Long> {
}
