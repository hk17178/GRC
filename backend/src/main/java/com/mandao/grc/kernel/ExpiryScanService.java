package com.mandao.grc.kernel;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 到期扫描内核：所有时间事件（*_DUE / *_EXPIRING / *_APPROACHING）的唯一生产者。
 *
 * 位于 {@code com.mandao.grc.kernel}（不在 modules 包）——故【不受 OrgScopeAspect 的用户级隔离】。
 * 内核是系统级actor，需跨全部 org 扫描，因此显式将会话置为"系统可见全部 org"。
 *
 * 设计要点（D1-1 §5.12 / D1-9 H-01）：
 *  - 只产事件、写入 domain_event 件箱，不做分发（分发由 M10/M9 消费）；
 *  - 幂等：reminder_dispatch_log 唯一约束 + ON CONFLICT DO NOTHING，同一提醒只产一次；
 *  - 单实例：pg_try_advisory_xact_lock，多实例部署下仅一个实例真正扫描。
 *
 * 本切片演示外审计划临近提醒：当 plan_start_date - today 命中 reminder_days 中某天，产
 * EXT_AUDIT_PLAN_APPROACHING。生产可按同一骨架扩展其它到期源（监管报送/认证有效期/复评等）。
 */
@Service
public class ExpiryScanService {

    /** 单实例扫描锁的 advisory key（本用途专用）。 */
    private static final long SCAN_LOCK_KEY = 770001L;

    @PersistenceContext
    private EntityManager em;

    /**
     * 执行一次到期扫描。以 today 为参数（而非内部取当前日期），便于测试确定性与补扫。
     */
    @Transactional
    public ScanResult scanOnce(LocalDate today) {
        // 1) 单实例锁：抢不到说明已有实例在扫描，本次跳过（避免重复产）
        Boolean locked = (Boolean) em.createNativeQuery("SELECT pg_try_advisory_xact_lock(:k)")
                .setParameter("k", SCAN_LOCK_KEY)
                .getSingleResult();
        if (!Boolean.TRUE.equals(locked)) {
            return new ScanResult(0, true);
        }

        // 2) 系统可见全部 org（内核跨租户扫描；org 表无 RLS 可直接读取）
        String allOrgs = (String) em.createNativeQuery(
                        "SELECT coalesce(string_agg(id::text, ','), '-1') FROM org")
                .getSingleResult();
        em.createNativeQuery("SET LOCAL app.visible_orgs = '" + allOrgs + "'").executeUpdate();

        // 3) 扫描命中：unnest 把 reminder_days 展开，SQL 内判定"恰好到提醒日"
        @SuppressWarnings("unchecked")
        List<Object[]> hits = em.createNativeQuery(
                        "SELECT ap.id, ap.org_id, ap.title, rd "
                                + "FROM audit_plan ap, unnest(ap.reminder_days) AS rd "
                                + "WHERE ap.external_status = 'PLANNED' "
                                + "AND (ap.plan_start_date - CAST(:today AS date)) = rd")
                .setParameter("today", today.toString())
                .getResultList();

        int emitted = 0;
        for (Object[] r : hits) {
            long planId = ((Number) r[0]).longValue();
            long orgId = ((Number) r[1]).longValue();
            String title = (String) r[2];
            int reminderDay = ((Number) r[3]).intValue();

            // 4) 幂等登记：仅当首次登记成功（插入 1 行）才产事件
            int inserted = em.createNativeQuery(
                            "INSERT INTO reminder_dispatch_log(object_type, object_id, event_type, threshold_key, org_id) "
                                    + "VALUES ('AUDIT_PLAN', :pid, 'EXT_AUDIT_PLAN_APPROACHING', :tk, :org) "
                                    + "ON CONFLICT (object_type, object_id, event_type, threshold_key) DO NOTHING")
                    .setParameter("pid", planId)
                    .setParameter("tk", String.valueOf(reminderDay))
                    .setParameter("org", orgId)
                    .executeUpdate();

            if (inserted == 1) {
                String payload = "{\"auditPlanId\":" + planId
                        + ",\"reminderDay\":" + reminderDay
                        + ",\"title\":\"" + jsonEscape(title) + "\"}";
                em.createNativeQuery(
                                "INSERT INTO domain_event(event_type, org_id, payload) "
                                        + "VALUES ('EXT_AUDIT_PLAN_APPROACHING', :org, CAST(:payload AS jsonb))")
                        .setParameter("org", orgId)
                        .setParameter("payload", payload)
                        .executeUpdate();
                emitted++;
            }
        }
        return new ScanResult(emitted, false);
    }

    /** 最小 JSON 字符串转义，防 payload 因标题含特殊字符而非法。 */
    private static String jsonEscape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
