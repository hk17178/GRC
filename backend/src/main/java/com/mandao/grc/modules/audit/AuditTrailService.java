package com.mandao.grc.modules.audit;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 看板与留痕查询服务（横切聚合·只读）：查询防篡改操作留痕、暴露链完整性校验。
 *
 * 隔离：方法 @Transactional 且位于 com.mandao.grc.modules 包 → 切面注入 visible_orgs，
 * 对 operation_log 的原生查询受 RLS 裁剪，只返回当前主体可见组织的留痕。
 *
 * 完整性：{@link #verify} 委托 {@link HashChainService#verify} 重算并校验某 org 整条链（防篡改卖点）。
 *
 * 设计依据：需求文档「看板与留痕」、D1-3 §8（ADR-C 哈希链）、D2-5。
 */
@Service
public class AuditTrailService {

    private static final int DEFAULT_LIMIT = 200;
    private static final int MAX_LIMIT = 1000;

    @PersistenceContext
    private EntityManager em;

    private final HashChainService hashChainService;

    public AuditTrailService(HashChainService hashChainService) {
        this.hashChainService = hashChainService;
    }

    /**
     * 查询操作留痕（可见组织范围内，可选按 对象/动作/操作人 过滤；按入库时间新→旧）。
     *
     * @param entity 操作对象（如 POLICY:5、FINDING:3），精确匹配，可空
     * @param action 动作类型（如 POLICY_APPROVE），精确匹配，可空
     * @param actor  操作人，精确匹配，可空
     * @param limit  返回条数上限（默认 200，封顶 1000）
     */
    @Transactional(readOnly = true)
    public List<OperationLogView> query(String entity, String action, String actor, Integer limit) {
        int lim = (limit == null || limit <= 0) ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);

        StringBuilder sql = new StringBuilder(
                "SELECT org_id, seq, action, actor, entity, detail, created_at_ms, curr_hash FROM operation_log WHERE 1=1");
        boolean hasEntity = entity != null && !entity.isBlank();
        boolean hasAction = action != null && !action.isBlank();
        boolean hasActor = actor != null && !actor.isBlank();
        if (hasEntity) {
            sql.append(" AND entity = :entity");
        }
        if (hasAction) {
            sql.append(" AND action = :action");
        }
        if (hasActor) {
            sql.append(" AND actor = :actor");
        }
        // lim 为已校验的整型，直接拼接（避免部分驱动对 LIMIT 绑定参数的兼容问题）；过滤值用绑定参数防注入。
        sql.append(" ORDER BY id DESC LIMIT ").append(lim);

        Query q = em.createNativeQuery(sql.toString());
        if (hasEntity) {
            q.setParameter("entity", entity);
        }
        if (hasAction) {
            q.setParameter("action", action);
        }
        if (hasActor) {
            q.setParameter("actor", actor);
        }

        @SuppressWarnings("unchecked")
        List<Object[]> rows = q.getResultList();
        return rows.stream()
                .map(r -> new OperationLogView(
                        ((Number) r[0]).longValue(),
                        ((Number) r[1]).longValue(),
                        (String) r[2],
                        (String) r[3],
                        (String) r[4],
                        (String) r[5],
                        ((Number) r[6]).longValue(),
                        (String) r[7]))
                .toList();
    }

    /** 校验某 org 整条链的完整性（防篡改）。org 不可见时链为空、返回 ok(count=0)。 */
    @Transactional(readOnly = true)
    public ChainVerifyResult verify(long orgId) {
        return hashChainService.verify(orgId);
    }
}
