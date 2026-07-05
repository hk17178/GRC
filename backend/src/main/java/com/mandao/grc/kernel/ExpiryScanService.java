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
 *
 * M11 扩展：新增报送日历到期源——当 reg_filing.statutory_deadline - today 命中 reminder_days 中某天，产
 * REG_FILING_DUE（法定时限预警红线）。与 audit_plan 段同构，复用 reminder_dispatch_log 幂等台账
 * (object_type='REG_FILING') 与 domain_event 件箱；reg_filing 表空则该段不产事件。
 */
@Service
public class ExpiryScanService {

    /** 单实例扫描锁的 advisory key（本用途专用）。 */
    private static final long SCAN_LOCK_KEY = LockKeys.EXPIRY_SCAN;

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
        // 注意：用 CAST(... AS text) 而非 id::text —— Hibernate 原生查询把 ':' 解析为命名参数，
        // PostgreSQL 的 '::' 强转写法会触发 "syntax error at or near :"。
        String allOrgs = (String) em.createNativeQuery(
                        "SELECT coalesce(string_agg(CAST(id AS text), ','), '-1') FROM org")
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

        // 6) M11 报送日历段（法定时限预警红线）：与 audit_plan 段同构。
        // statutory_deadline - today 命中 reminder_days 某天即到提醒日；id::text 一类强转在原生查询里改 CAST。
        @SuppressWarnings("unchecked")
        List<Object[]> filingHits = em.createNativeQuery(
                        "SELECT rf.id, rf.org_id, rf.title, rd "
                                + "FROM reg_filing rf, unnest(rf.reminder_days) AS rd "
                                + "WHERE (rf.statutory_deadline - CAST(:today AS date)) = rd")
                .setParameter("today", today.toString())
                .getResultList();

        for (Object[] r : filingHits) {
            long filingId = ((Number) r[0]).longValue();
            long orgId = ((Number) r[1]).longValue();
            String title = (String) r[2];
            int reminderDay = ((Number) r[3]).intValue();

            // 幂等登记：仅当首次登记成功（插入 1 行）才产事件
            int inserted = em.createNativeQuery(
                            "INSERT INTO reminder_dispatch_log(object_type, object_id, event_type, threshold_key, org_id) "
                                    + "VALUES ('REG_FILING', :fid, 'REG_FILING_DUE', :tk, :org) "
                                    + "ON CONFLICT (object_type, object_id, event_type, threshold_key) DO NOTHING")
                    .setParameter("fid", filingId)
                    .setParameter("tk", String.valueOf(reminderDay))
                    .setParameter("org", orgId)
                    .executeUpdate();

            if (inserted == 1) {
                String payload = "{\"regFilingId\":" + filingId
                        + ",\"reminderDay\":" + reminderDay
                        + ",\"title\":\"" + jsonEscape(title) + "\"}";
                em.createNativeQuery(
                                "INSERT INTO domain_event(event_type, org_id, payload) "
                                        + "VALUES ('REG_FILING_DUE', :org, CAST(:payload AS jsonb))")
                        .setParameter("org", orgId)
                        .setParameter("payload", payload)
                        .executeUpdate();
                emitted++;
            }
        }

        // 7) 七轮 7-2/B3：重大事件法定报送时限段——未上报（DRAFT）且距 report_deadline
        // 恰余 3/1/0 天时产 MAJOR_INCIDENT_REPORT_DUE（支付机构法定时限预警红线）。
        @SuppressWarnings("unchecked")
        List<Object[]> incidentHits = em.createNativeQuery(
                        "SELECT mi.id, mi.org_id, mi.title, rd "
                                + "FROM major_incident_report mi, unnest(ARRAY[3,1,0]) AS rd "
                                + "WHERE mi.status = 'DRAFT' AND mi.report_deadline IS NOT NULL "
                                + "AND (mi.report_deadline - CAST(:today AS date)) = rd")
                .setParameter("today", today.toString())
                .getResultList();

        for (Object[] r : incidentHits) {
            long incidentId = ((Number) r[0]).longValue();
            long orgId = ((Number) r[1]).longValue();
            String title = (String) r[2];
            int reminderDay = ((Number) r[3]).intValue();

            int inserted = em.createNativeQuery(
                            "INSERT INTO reminder_dispatch_log(object_type, object_id, event_type, threshold_key, org_id, message) "
                                    + "VALUES ('MAJOR_INCIDENT', :iid, 'MAJOR_INCIDENT_REPORT_DUE', :tk, :org, :msg) "
                                    + "ON CONFLICT (object_type, object_id, event_type, threshold_key) DO NOTHING")
                    .setParameter("iid", incidentId)
                    .setParameter("tk", String.valueOf(reminderDay))
                    .setParameter("org", orgId)
                    .setParameter("msg", "重大事件「" + title + "」距法定报送时限仅剩 " + reminderDay
                            + " 天仍未上报监管，请立即处理（法定时限红线）")
                    .executeUpdate();

            if (inserted == 1) {
                String payload = "{\"incidentId\":" + incidentId
                        + ",\"reminderDay\":" + reminderDay
                        + ",\"title\":\"" + jsonEscape(title) + "\"}";
                em.createNativeQuery(
                                "INSERT INTO domain_event(event_type, org_id, payload) "
                                        + "VALUES ('MAJOR_INCIDENT_REPORT_DUE', :org, CAST(:payload AS jsonb))")
                        .setParameter("org", orgId)
                        .setParameter("payload", payload)
                        .executeUpdate();
                emitted++;
            }
        }

        // 8) 安全加固包 A18：签名票据日清——一次性令牌超过 24 小时无保留价值（正本已入评估存证），
        // 连行删除，防过期票据里的签名字节长期滞留。
        em.createNativeQuery("DELETE FROM signature_ticket WHERE created_at < now() - INTERVAL '24 hours'")
                .executeUpdate();

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
