package com.mandao.grc.modules.regulatory;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 报送日历仓储。
 *
 * 不写任何 org 过滤条件：在已注入 app.visible_orgs 的连接上，RLS 自动只返回可见组织的行
 * （与 AuditPlanRepository 同范式）。
 */
public interface RegFilingRepository extends JpaRepository<RegFiling, Long> {
}
