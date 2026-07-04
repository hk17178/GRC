package com.mandao.grc.modules.atv;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 风险场景仓储。不写 org 过滤：RLS 自动裁剪可见行。 */
public interface RiskScenarioRepository extends JpaRepository<RiskScenario, Long> {

    /** 列出某资产的全部风险场景。 */
    List<RiskScenario> findByAssetId(Long assetId);

    /** 引用检查（八轮 8-10：威胁/脆弱性被场景引用则不可删）。 */
    boolean existsByThreatId(Long threatId);

    boolean existsByVulnerabilityId(Long vulnerabilityId);

    /** 判重：同一资产-威胁-脆弱组合是否已存在。 */
    boolean existsByAssetIdAndThreatIdAndVulnerabilityId(Long assetId, Long threatId, Long vulnerabilityId);
}
