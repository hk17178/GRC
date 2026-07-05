package com.mandao.grc.kernel;

import jakarta.persistence.EntityManager;

/**
 * 内核会话可见域设置的共享工具（架构治理包 A26）。
 *
 * 内核服务（不受 OrgScopeAspect 用户级隔离）需在事务内显式把会话置为"系统可见全部 org"。
 * 此前各调度器各自 {@code "SET LOCAL app.visible_orgs = '" + csv + "'"} 字符串拼接——
 * 虽然 csv 来自 org 表 string_agg（纯数字非用户输入，实际无注入风险），但平台自身应做
 * G-11 防注入样板。这里统一收敛为 set_config 参数化调用（值走绑定参数，不进 SQL 文本）。
 */
public final class VisibleOrgsSql {

    private VisibleOrgsSql() {
    }

    /**
     * 把当前事务会话置为"可见全部 org"（系统级跨租户扫描）。
     * set_config(key, value, is_local=true) 等价于 SET LOCAL，但 value 走绑定参数。
     */
    public static void setAllOrgs(EntityManager em) {
        String allOrgs = (String) em.createNativeQuery(
                        "SELECT coalesce(string_agg(CAST(id AS text), ','), '-1') FROM org")
                .getSingleResult();
        em.createNativeQuery("SELECT set_config('app.visible_orgs', :orgs, true)")
                .setParameter("orgs", allOrgs)
                .getSingleResult();
    }
}
