package com.mandao.grc.modules.workflow;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 流程绑定仓储（D1-8 H-06）。RLS 按 visible_orgs 裁剪。 */
public interface ProcessBindingRepository extends JpaRepository<ProcessBinding, Long> {

    List<ProcessBinding> findByObjectTypeOrderBySeqAscIdAsc(String objectType);

    /** 匹配用：某对象类型的启用绑定，按优先级（seq 小者先，同序按 id）。 */
    List<ProcessBinding> findByObjectTypeAndStatusOrderBySeqAscIdAsc(String objectType, String status);
}
