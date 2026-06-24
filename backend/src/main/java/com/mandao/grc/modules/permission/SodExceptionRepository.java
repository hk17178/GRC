package com.mandao.grc.modules.permission;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * SoD 豁免仓储（org-scoped）。
 *
 * 不写任何 org 过滤条件：在已注入 app.visible_orgs 的连接上，RLS 自动只返回可见组织的行。
 */
public interface SodExceptionRepository extends JpaRepository<SodException, Long> {

    /** 某 org 下某 user 针对某互斥规则的豁免（全部状态）。 */
    List<SodException> findByOrgIdAndUserIdAndSodRuleId(Long orgId, Long userId, Long sodRuleId);

    /** 某 org 下某 user 针对某互斥规则、指定状态的豁免——enforceSod 仅以 APPROVED 视为有效放行。 */
    List<SodException> findByOrgIdAndUserIdAndSodRuleIdAndStatus(
            Long orgId, Long userId, Long sodRuleId, SodExceptionStatus status);
}
