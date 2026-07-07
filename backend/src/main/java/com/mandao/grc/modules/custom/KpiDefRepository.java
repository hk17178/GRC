package com.mandao.grc.modules.custom;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 自定义 KPI 定义仓储（B12 Phase4）。RLS 按 visible_orgs 裁剪。 */
public interface KpiDefRepository extends JpaRepository<KpiDef, Long> {

    List<KpiDef> findByObjectTypeOrderByIdAsc(String objectType);
}
