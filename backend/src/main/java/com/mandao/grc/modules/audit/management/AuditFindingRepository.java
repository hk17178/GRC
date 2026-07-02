package com.mandao.grc.modules.audit.management;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 审计发现仓储。
 *
 * 不写任何 org 过滤条件：在已注入 app.visible_orgs 的连接上，RLS 自动只返回可见组织的行
 * （与 RiskFindingRepository 同范式）。
 */
public interface AuditFindingRepository extends JpaRepository<AuditFinding, Long> {

    /** 列出某审计计划下的全部发现（仍受 RLS 裁剪）。 */
    List<AuditFinding> findByAuditPlanId(Long auditPlanId);

    /** 按审计类型跨计划列发现（外审页"外部审计发现"Tab：所有 EXTERNAL 计划的发现）。 */
    @Query("SELECT f FROM AuditFinding f WHERE f.auditPlanId IN "
            + "(SELECT p.id FROM AuditPlan p WHERE p.auditType = :type) ORDER BY f.id DESC")
    List<AuditFinding> findByPlanType(@Param("type") AuditType type);
}
