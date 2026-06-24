package com.mandao.grc.modules.permission;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 权限四元组仓储（org-scoped）。
 *
 * 不写任何 org 过滤条件：在已注入 app.visible_orgs 的连接上，RLS 自动只返回可见组织的行
 * （与 AuditFindingRepository/RiskFindingRepository 同范式）。
 */
public interface UserRoleOrgRepository extends JpaRepository<UserRoleOrg, Long> {

    /** 某 org 下某 user 的全部授权行（含已回收 active=false，仍受 RLS 裁剪）。 */
    List<UserRoleOrg> findByOrgIdAndUserId(Long orgId, Long userId);

    /** 某 org 下某 user 的有效授权行（active=true）。 */
    List<UserRoleOrg> findByOrgIdAndUserIdAndActiveTrue(Long orgId, Long userId);

    /** 精确定位四元组（用于幂等授权/回收）。 */
    Optional<UserRoleOrg> findByOrgIdAndUserIdAndRoleId(Long orgId, Long userId, Long roleId);

    /** 某 org 下全部有效授权（UAR 建项的候选）。 */
    List<UserRoleOrg> findByOrgIdAndActiveTrue(Long orgId);
}
