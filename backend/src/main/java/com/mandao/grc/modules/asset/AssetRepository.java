package com.mandao.grc.modules.asset;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 资产台账仓储。RLS 自动按 visible_orgs 裁剪，无手写 org 过滤
 * （与 AssessmentRepository/RegFilingRepository 同范式）。
 *
 * 合规属性筛查用派生查询（仍受 RLS 裁剪，仅返回可见组织内匹配的资产）：
 * 如 findByContainsPiTrue 查所有含个人信息的资产。
 */
public interface AssetRepository extends JpaRepository<Asset, Long> {

    /** 含个人信息（PI）的资产。 */
    List<Asset> findByContainsPiTrue();

    /** 涉及数据跨境的资产。 */
    List<Asset> findByCrossBorderTrue();

    /** 含持卡人数据（CHD）的资产。 */
    List<Asset> findByContainsChdTrue();

    /** 已等保备案（MLPS）的资产。 */
    List<Asset> findByMlpsFiledTrue();

    /** 按分类分级筛查。 */
    List<Asset> findByClassification(AssetClassification classification);
}
