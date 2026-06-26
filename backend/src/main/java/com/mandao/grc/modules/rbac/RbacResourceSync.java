package com.mandao.grc.modules.rbac;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 资源目录同步（增强③ R6）：把 {@link ResourceCatalog} 幂等 upsert 到 resource 表。
 *
 * 按 code 冲突时更新名称/类型/归属/排序（ON CONFLICT DO UPDATE）；新增目录项自动入库。
 * 不删除库中多余资源（可能已有 role_resource 授权），仅增量同步。resource 无 RLS。
 */
@Service
public class RbacResourceSync {

    @PersistenceContext
    private EntityManager em;

    /** 幂等 upsert 全部目录资源；返回处理条数。 */
    @Transactional
    public int upsertAll() {
        int n = 0;
        for (ResourceCatalog.Def d : ResourceCatalog.ALL) {
            em.createNativeQuery(
                            "INSERT INTO resource (code, name, type, parent_menu, sort) " +
                            "VALUES (:c, :n, :t, :p, :s) " +
                            "ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name, type = EXCLUDED.type, " +
                            "parent_menu = EXCLUDED.parent_menu, sort = EXCLUDED.sort")
                    .setParameter("c", d.code()).setParameter("n", d.name())
                    .setParameter("t", d.type()).setParameter("p", d.parentMenu())
                    .setParameter("s", d.sort())
                    .executeUpdate();
            n++;
        }
        return n;
    }
}
