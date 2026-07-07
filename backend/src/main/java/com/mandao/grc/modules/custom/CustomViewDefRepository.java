package com.mandao.grc.modules.custom;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 自定义视图定义仓储（B12 Phase2）。RLS 按 visible_orgs 裁剪。 */
public interface CustomViewDefRepository extends JpaRepository<CustomViewDef, Long> {

    List<CustomViewDef> findByObjectTypeOrderByIdAsc(String objectType);
}
