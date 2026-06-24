package com.mandao.grc.modules.audit.management;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 整改工单仓储。不写 org 过滤：RLS 自动裁剪可见行。 */
public interface RemediationOrderRepository extends JpaRepository<RemediationOrder, Long> {

    /** 列出某审计发现的全部整改工单。 */
    List<RemediationOrder> findByFindingId(Long findingId);

    /** 验证闭环判定：某发现是否已有指定状态的工单（如 VERIFIED）。 */
    boolean existsByFindingIdAndStatus(Long findingId, RemediationStatus status);
}
