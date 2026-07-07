package com.mandao.grc.modules.custom;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 自定义字段定义仓储（B12 Phase1）。RLS 按 visible_orgs 裁剪。 */
public interface CustomFieldDefRepository extends JpaRepository<CustomFieldDef, Long> {

    /** 某对象类型的字段（按 seq 排序，含 RETIRED——列表页展示）。 */
    List<CustomFieldDef> findByObjectTypeOrderBySeqAscIdAsc(String objectType);

    /** 某对象类型的启用字段（校验/渲染用）。 */
    List<CustomFieldDef> findByObjectTypeAndStatusOrderBySeqAscIdAsc(String objectType, String status);
}
