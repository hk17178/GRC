package com.mandao.grc.modules.rbac;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RBAC 配置管理（增强③ R5）：角色 / 资源 / 权限矩阵的读写。
 *
 * role / resource / role_resource 均为全局字典（无 RLS）；经原生 SQL 读写。
 * 写矩阵采用"先清后插"：删除该角色全部 role_resource，再插入非 HIDDEN 的项（HIDDEN=默认拒绝，不存行）。
 * 端点层以 @RequiresPermission 限制仅有权角色可改。
 */
@Service
public class RbacAdminService {

    @PersistenceContext
    private EntityManager em;

    /** 角色列表（含超管标记）。 */
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listRoles() {
        List<Object[]> rows = em.createNativeQuery(
                "SELECT id, code, name, superadmin FROM role ORDER BY id").getResultList();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object[] r : rows) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", ((Number) r[0]).longValue());
            m.put("code", r[1]);
            m.put("name", r[2]);
            m.put("superadmin", r[3]);
            out.add(m);
        }
        return out;
    }

    /** 某角色的权限矩阵 {resourceCode: level}（仅已授权项）。 */
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public Map<String, String> rolePermissions(Long roleId) {
        List<Object[]> rows = em.createNativeQuery(
                        "SELECT resource_code, level FROM role_resource WHERE role_id = :id")
                .setParameter("id", roleId).getResultList();
        Map<String, String> m = new HashMap<>();
        for (Object[] r : rows) {
            m.put((String) r[0], (String) r[1]);
        }
        return m;
    }

    /** 保存某角色的权限矩阵（先清后插；HIDDEN 不存行=默认拒绝）。 */
    @Transactional
    public void setRolePermissions(Long roleId, Map<String, String> levels) {
        em.createNativeQuery("DELETE FROM role_resource WHERE role_id = :id")
                .setParameter("id", roleId).executeUpdate();
        if (levels == null) {
            return;
        }
        for (Map.Entry<String, String> e : levels.entrySet()) {
            String level = e.getValue();
            if (level == null || "HIDDEN".equals(level)) {
                continue;
            }
            em.createNativeQuery("INSERT INTO role_resource (role_id, resource_code, level) VALUES (:r, :c, :l)")
                    .setParameter("r", roleId).setParameter("c", e.getKey()).setParameter("l", level)
                    .executeUpdate();
        }
    }

    /** 新建角色（普通角色，非超管）。 */
    @Transactional
    public Long createRole(String code, String name) {
        em.createNativeQuery("INSERT INTO role (code, name, superadmin) VALUES (:c, :n, false)")
                .setParameter("c", code).setParameter("n", name).executeUpdate();
        Number id = (Number) em.createNativeQuery("SELECT id FROM role WHERE code = :c")
                .setParameter("c", code).getSingleResult();
        return id.longValue();
    }
}
