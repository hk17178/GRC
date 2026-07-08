package com.mandao.grc.kernel;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 通知场景升级链运行器（D1-8 §九 接线二）。
 *
 * 周期扫描 status=PENDING 的 scene_notification，按其场景升级链（notification_escalation，
 * 级别升序）逐级判断：距首发（created_at）已过 delay_hours 且该级尚未触发者，写一条
 * scene_escalation_log（升级到 escalate_to_role）并把 current_level 推进到该级。
 * 一次运行可补齐多个已到期级别（curLevel+1、+2…直到遇到未到期级别为止）。
 *
 * 已 ACKED 的通知不再升级（处理即止链）。与其它内核调度同范式：advisory 单实例锁 +
 * setAllOrgs 系统级跨租户扫描 + 唯一约束幂等（同通知同级别只触发一次）。
 * now 由调用方注入（生产传 OffsetDateTime.now()，测试可注入未来时刻以确定性验证到期）。
 */
@Service
public class EscalationRunner {

    private static final Logger log = LoggerFactory.getLogger(EscalationRunner.class);
    private static final long LOCK_KEY = LockKeys.SCENE_ESCALATION;

    @PersistenceContext
    private EntityManager em;

    /** 评估一轮升级；返回本轮真正触发的升级级数。 */
    @Transactional
    public int runOnce(OffsetDateTime now) {
        Boolean locked = (Boolean) em.createNativeQuery("SELECT pg_try_advisory_xact_lock(:k)")
                .setParameter("k", LOCK_KEY)
                .getSingleResult();
        if (!Boolean.TRUE.equals(locked)) {
            return 0;
        }
        // 系统级跨租户扫描（与规则引擎/到期扫描同口径）
        VisibleOrgsSql.setAllOrgs(em);

        @SuppressWarnings("unchecked")
        List<Object[]> pending = em.createNativeQuery(
                        "SELECT id, org_id, scene_id, current_level, message FROM scene_notification "
                                + "WHERE status = 'PENDING'")
                .getResultList();

        int fired = 0;
        for (Object[] n : pending) {
            long notifId = ((Number) n[0]).longValue();
            long orgId = ((Number) n[1]).longValue();
            long sceneId = ((Number) n[2]).longValue();
            int curLevel = ((Number) n[3]).intValue();
            String message = (String) n[4];

            // 该通知高于当前级别的升级链，携"是否已到期"（时间比较交给 PG，正确处理 TZ）
            @SuppressWarnings("unchecked")
            List<Object[]> chain = em.createNativeQuery(
                            "SELECT e.level, e.escalate_to_role, "
                                    + "(:now >= n.created_at + make_interval(hours => e.delay_hours)) AS due "
                                    + "FROM notification_escalation e "
                                    + "JOIN scene_notification n ON n.id = :nid "
                                    + "WHERE e.scene_id = :sid AND e.status = 'ACTIVE' AND e.level > :cur "
                                    + "ORDER BY e.level ASC")
                    .setParameter("now", now)
                    .setParameter("nid", notifId)
                    .setParameter("sid", sceneId)
                    .setParameter("cur", curLevel)
                    .getResultList();

            for (Object[] step : chain) {
                int level = ((Number) step[0]).intValue();
                String role = (String) step[1];
                boolean due = Boolean.TRUE.equals(step[2]);
                if (!due) {
                    break;   // 链按级升序、延迟递增：本级未到期，更高级更不到期
                }
                int inserted = em.createNativeQuery(
                                "INSERT INTO scene_escalation_log(org_id, notification_id, level, escalate_to_role, message) "
                                        + "VALUES (:org, :nid, :lvl, :role, :msg) "
                                        + "ON CONFLICT (notification_id, level) DO NOTHING")
                        .setParameter("org", orgId)
                        .setParameter("nid", notifId)
                        .setParameter("lvl", level)
                        .setParameter("role", role)
                        .setParameter("msg", "【升级·L" + level + "→" + role + "】" + message)
                        .executeUpdate();
                em.createNativeQuery("UPDATE scene_notification SET current_level = :lvl WHERE id = :nid")
                        .setParameter("lvl", level)
                        .setParameter("nid", notifId)
                        .executeUpdate();
                fired += inserted;
            }
        }
        if (fired > 0) {
            log.info("通知场景升级链触发 {} 级", fired);
        }
        return fired;
    }
}
