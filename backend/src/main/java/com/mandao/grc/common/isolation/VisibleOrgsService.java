package com.mandao.grc.common.isolation;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 计算主体的可见组织集合 visibleOrgs。
 *
 * 生产实现：user → user_role_org → org（按授权 + 组织子树展开），见 D1-3 §5.1。
 * 本切片（PoC）按用户所属组织推导：
 *   - GROUP（集团）用户：本节点 + 全部子孙组织（物化路径前缀）；
 *   - SUBSIDIARY（子公司）用户：仅本组织。
 *
 * 注意：org / app_user 未启用 RLS，故 grc_app 可正常读取以计算可见域；
 * 仅业务表（assessment 等）受 RLS 约束。
 */
@Service
public class VisibleOrgsService {

    @PersistenceContext
    private EntityManager em;

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<Long> computeFor(String username) {
        List<Object[]> rows = em.createNativeQuery(
                "SELECT o.id, o.org_type, o.path FROM app_user u " +
                "JOIN org o ON o.id = u.org_id WHERE u.username = :n")
                .setParameter("n", username)
                .getResultList();

        if (rows.isEmpty()) {
            return List.of(); // 未知主体 → 空可见域 → 默认拒绝
        }
        Object[] row = rows.get(0);
        Long orgId = ((Number) row[0]).longValue();
        String orgType = (String) row[1];
        String path = (String) row[2];

        if ("GROUP".equals(orgType)) {
            List<Object> ids = em.createNativeQuery(
                    "SELECT id FROM org WHERE path = :p OR path LIKE :prefix")
                    .setParameter("p", path)
                    .setParameter("prefix", path + "/%")
                    .getResultList();
            return ids.stream().map(x -> ((Number) x).longValue()).toList();
        }
        return List.of(orgId);
    }

    /** 用户当前会话纪元 token_epoch（M-15）；用户不存在返回 -1。 */
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public int currentTokenEpoch(String username) {
        List<Object> r = em.createNativeQuery("SELECT token_epoch FROM app_user WHERE username = :n")
                .setParameter("n", username)
                .getResultList();
        return r.isEmpty() ? -1 : ((Number) r.get(0)).intValue();
    }
}
