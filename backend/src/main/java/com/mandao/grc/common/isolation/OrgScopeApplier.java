package com.mandao.grc.common.isolation;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 在当前事务中注入数据库会话变量 app.visible_orgs，供 RLS 策略消费。
 *
 * 关键点：
 *  - 使用 SET LOCAL（事务级），事务结束自动复位，避免连接池复用串号；
 *  - 必须在 @Transactional 方法内、查询之前调用，确保与查询同一连接；
 *  - visibleOrgs 为空时注入 '-1'（不匹配任何组织）→ 默认拒绝（fail-closed）。
 *
 * 设计依据：D1-3 §5.1。RLS 是兜底，本注入是第一道；二者叠加构成隔离红线。
 */
@Component
public class OrgScopeApplier {

    @PersistenceContext
    private EntityManager em;

    public void apply() {
        List<Long> orgs = IsolationContext.get();
        String csv = (orgs == null || orgs.isEmpty())
                ? "-1"
                : orgs.stream().map(String::valueOf).collect(Collectors.joining(","));
        // csv 仅由 Long 拼接，无注入风险；SET LOCAL 不支持绑定参数故内联。
        em.createNativeQuery("SET LOCAL app.visible_orgs = '" + csv + "'").executeUpdate();
    }
}
