package com.mandao.grc.modules.audit.management;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** 整改工单仓储。不写 org 过滤：RLS 自动裁剪可见行。 */
public interface RemediationOrderRepository extends JpaRepository<RemediationOrder, Long> {

    /** 列出某审计发现的全部整改工单。 */
    List<RemediationOrder> findByFindingId(Long findingId);

    /** 验证闭环判定：某发现是否已有指定状态的工单（如 VERIFIED）。 */
    boolean existsByFindingIdAndStatus(Long findingId, RemediationStatus status);

    /** 按审计类型跨发现列整改工单（外审页"整改跟踪"Tab：所有 EXTERNAL 计划发现的工单）。 */
    @Query("SELECT r FROM RemediationOrder r WHERE r.findingId IN "
            + "(SELECT f.id FROM AuditFinding f WHERE f.auditPlanId IN "
            + "  (SELECT p.id FROM AuditPlan p WHERE p.auditType = :type)) ORDER BY r.id DESC")
    List<RemediationOrder> findByPlanType(@Param("type") AuditType type);
}
