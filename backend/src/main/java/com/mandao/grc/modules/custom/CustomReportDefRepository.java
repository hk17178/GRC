package com.mandao.grc.modules.custom;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 自定义报表定义仓储（B12 Phase3）。RLS 按 visible_orgs 裁剪。 */
public interface CustomReportDefRepository extends JpaRepository<CustomReportDef, Long> {

    List<CustomReportDef> findByObjectTypeOrderByIdAsc(String objectType);
}
