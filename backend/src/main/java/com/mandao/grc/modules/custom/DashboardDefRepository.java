package com.mandao.grc.modules.custom;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 自定义看板定义仓储（B12 Phase5）。RLS 按 visible_orgs 裁剪。 */
public interface DashboardDefRepository extends JpaRepository<DashboardDef, Long> {

    List<DashboardDef> findByStatusOrderByIdAsc(String status);

    List<DashboardDef> findByOrderByIdAsc();
}
