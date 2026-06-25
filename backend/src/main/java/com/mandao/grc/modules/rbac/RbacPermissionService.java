package com.mandao.grc.modules.rbac;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 有效权限解析（功能级 RBAC 核心）。
 *
 * 计算用户对各资源(菜单/动作)的有效级别：用户所有有效角色对同资源取最高(RW>RO>HIDDEN)；
 * 未授权资源不返回（前端按缺省=HIDDEN 处理，默认拒绝）。
 * 超管角色(role.superadmin)短路为对全部资源 RW（免铺全量矩阵行）。
 *
 * 隔离：方法 @Transactional 且位于 modules 包 → OrgScopeAspect 注入 visible_orgs；
 * 读 user_role_org(RLS) 时当前用户自身在其组织内的授权可见。resource/role_resource 无 RLS。
 */
@Service
public class RbacPermissionService {

    @PersistenceContext
    private EntityManager em;

    /** 用户的有效权限映射 {resourceCode: RW/RO}（不含 HIDDEN）。 */
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public Map<String, String> effectiveFor(String username) {
        Map<String, String> result = new HashMap<>();
        if (username == null || username.isBlank()) {
            return result;
        }
        // 超管短路：对全部资源 RW
        Number superCount = (Number) em.createNativeQuery(
                        "SELECT count(*) FROM app_user u " +
                        "JOIN user_role_org uro ON uro.user_id = u.id AND uro.active = true " +
                        "JOIN role r ON r.id = uro.role_id " +
                        "WHERE u.username = :n AND r.superadmin = true")
                .setParameter("n", username).getSingleResult();
        if (superCount.intValue() > 0) {
            for (Object code : em.createNativeQuery("SELECT code FROM resource").getResultList()) {
                result.put((String) code, "RW");
            }
            return result;
        }
        // 普通角色：聚合 role_resource，取最高级别
        List<Object[]> rows = em.createNativeQuery(
                        "SELECT rr.resource_code, rr.level FROM app_user u " +
                        "JOIN user_role_org uro ON uro.user_id = u.id AND uro.active = true " +
                        "JOIN role_resource rr ON rr.role_id = uro.role_id " +
                        "WHERE u.username = :n")
                .setParameter("n", username).getResultList();
        for (Object[] row : rows) {
            String code = (String) row[0];
            String level = (String) row[1];
            result.merge(code, level, (a, b) -> rank(b) > rank(a) ? b : a);
        }
        result.values().removeIf("HIDDEN"::equals); // 显式 HIDDEN 也按默认拒绝剔除
        return result;
    }

    /** 是否对某资源有读写权（R3 后端强制用）。 */
    @Transactional(readOnly = true)
    public boolean canWrite(String username, String resourceCode) {
        return "RW".equals(effectiveFor(username).get(resourceCode));
    }

    private int rank(String level) {
        return "RW".equals(level) ? 2 : "RO".equals(level) ? 1 : 0;
    }
}
