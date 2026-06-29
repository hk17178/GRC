package com.mandao.grc.modules.audit.management;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 审计计划仓储。
 *
 * 不写任何 org 过滤条件：在已注入 app.visible_orgs 的连接上，RLS 自动只返回可见组织的行
 * （与 PolicyRepository/AssessmentRepository 同范式）。
 */
public interface AuditPlanRepository extends JpaRepository<AuditPlan, Long> {

    /** 按审计类型过滤（内部审计/外部审计/监管审计分视图）。 */
    List<AuditPlan> findByAuditTypeOrderByIdDesc(AuditType auditType);
}
