package com.mandao.grc.common.isolation;

import java.util.List;

/**
 * 请求级隔离上下文：承载当前主体的可见组织集合 visibleOrgs。
 * 由 {@link IsolationFilter} 在请求进入时设置、退出时清理；
 * 由 {@link OrgScopeApplier} 在事务开始时注入数据库会话变量 app.visible_orgs。
 *
 * 设计依据：D1-3 §5.1（统一数据访问层注入 visibleOrgs）。
 */
public final class IsolationContext {

    private static final ThreadLocal<List<Long>> VISIBLE_ORGS = new ThreadLocal<>();

    private IsolationContext() {}

    public static void set(List<Long> orgIds) {
        VISIBLE_ORGS.set(orgIds);
    }

    public static List<Long> get() {
        return VISIBLE_ORGS.get();
    }

    public static void clear() {
        VISIBLE_ORGS.remove();
    }
}
