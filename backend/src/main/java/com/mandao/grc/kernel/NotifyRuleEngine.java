package com.mandao.grc.kernel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 通知规则引擎（六轮 UAT #7）：把通知中心的「规则」从纯展示配置变为可执行的告警逻辑。
 *
 * 规则存于 notify_config(kind='RULE')，detail 为 JSON：
 *   {"source":"数据源","days":条件天数,"channel":"通道","template":"含 {变量} 的内容模板"}
 * 支持的数据源（均查真实业务表）：
 *   - REMEDIATION_OVERDUE ：整改单已过期仍未提交（status PENDING/IN_PROGRESS 且 due_date &lt; today）
 *   - ASSESSMENT_STALLED  ：风险评估待复核滞留超 days 天（status=PENDING_REVIEW）
 *   - REG_NEW             ：追踪源近 days 天新采集的法规条目
 *   - KRI_BREACH          ：KRI 最新一次测量触及 WARNING/CRITICAL 阈值
 *
 * 与 {@link ExpiryScanService} 同范式：kernel 包（不受用户级隔离切面）、系统级跨 org 扫描、
 * advisory 锁单实例、reminder_dispatch_log 唯一约束 + ON CONFLICT DO NOTHING 幂等；
 * 渲染后的消息写入 message 列，来源规则写入 rule_id，通知中心「触发历史」与工作台通知直接展示。
 * 模板变量用 {变量} 花括号语法（不用美元符——Flyway 迁移里美元花括号会被当占位符解析）。
 *
 * 八轮 8-1：新产告警可经 collector 收集，由 {@link AlertPushService} 在【事务外】做企微外推。
 */
@Service
public class NotifyRuleEngine {

    private static final Logger log = LoggerFactory.getLogger(NotifyRuleEngine.class);

    /** 单实例锁 advisory key（集中登记于 LockKeys，七轮修复与定时抓取的撞号）。 */
    private static final long ENGINE_LOCK_KEY = LockKeys.NOTIFY_RULES;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @PersistenceContext
    private EntityManager em;

    /** 评估全部启用规则一轮，返回本轮新产出的通知条数。 */
    @Transactional
    public int runOnce(LocalDate today) {
        return runOnce(today, null);
    }

    /**
     * 评估一轮并收集新产告警（八轮 8-1：供 {@link AlertPushService} 在事务提交后做通道外推）。
     * collector 可空；只收真正新插入的告警（幂等冲突不收）。
     */
    @Transactional
    public int runOnce(LocalDate today, List<AlertPushService.Alert> collector) {
        Boolean locked = (Boolean) em.createNativeQuery("SELECT pg_try_advisory_xact_lock(:k)")
                .setParameter("k", ENGINE_LOCK_KEY)
                .getSingleResult();
        if (!Boolean.TRUE.equals(locked)) {
            return 0;
        }

        // 系统级跨租户扫描：会话可见全部 org（与 ExpiryScanService 同口径）
        String allOrgs = (String) em.createNativeQuery(
                        "SELECT coalesce(string_agg(CAST(id AS text), ','), '-1') FROM org")
                .getSingleResult();
        em.createNativeQuery("SET LOCAL app.visible_orgs = '" + allOrgs + "'").executeUpdate();

        @SuppressWarnings("unchecked")
        List<Object[]> rules = em.createNativeQuery(
                        "SELECT id, org_id, detail FROM notify_config WHERE kind = 'RULE' AND enabled = TRUE")
                .getResultList();

        int produced = 0;
        for (Object[] rule : rules) {
            long ruleId = ((Number) rule[0]).longValue();
            String detail = (String) rule[2];
            try {
                JsonNode cfg = MAPPER.readTree(detail == null ? "{}" : detail);
                String source = cfg.path("source").asText("");
                int days = cfg.path("days").asInt(0);
                String template = cfg.path("template").asText("");
                if (template.isBlank()) {
                    continue;
                }
                produced += switch (source) {
                    case "REMEDIATION_OVERDUE" -> scanRemediationOverdue(ruleId, template, today, collector);
                    case "ASSESSMENT_STALLED" -> scanAssessmentStalled(ruleId, template, today, days, collector);
                    case "REG_NEW" -> scanRegNew(ruleId, template, today, Math.max(days, 1), collector);
                    case "KRI_BREACH" -> scanKriBreach(ruleId, template, collector);
                    default -> 0; // 未知数据源：跳过（旧格式或手工误配），不让单条脏规则拖垮整轮
                };
            } catch (RuntimeException | com.fasterxml.jackson.core.JacksonException e) {
                log.warn("notify-rule {} evaluate failed: {}", ruleId, e.getMessage());
            }
        }
        return produced;
    }

    /** 整改逾期：status 未完结且 due_date 已过。变量：{标题} {逾期天数} {责任人}。 */
    private int scanRemediationOverdue(long ruleId, String template, LocalDate today,
                                       List<AlertPushService.Alert> collector) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                        "SELECT id, org_id, measure, assignee, (CAST(:today AS date) - due_date) AS overdue "
                                + "FROM remediation_order "
                                + "WHERE status IN ('PENDING','IN_PROGRESS') AND due_date < CAST(:today AS date)")
                .setParameter("today", today.toString())
                .getResultList();
        int n = 0;
        for (Object[] r : rows) {
            String msg = template
                    .replace("{标题}", nullSafe((String) r[2]))
                    .replace("{逾期天数}", String.valueOf(((Number) r[4]).intValue()))
                    .replace("{责任人}", nullSafe((String) r[3]));
            n += dispatch("REMEDIATION", ((Number) r[0]).longValue(), "RULE_REMEDIATION_OVERDUE",
                    "rule=" + ruleId, ((Number) r[1]).longValue(), msg, ruleId, collector);
        }
        return n;
    }

    /** 评估复核滞留：PENDING_REVIEW 超 days 天。变量：{标题} {滞留天数}。 */
    private int scanAssessmentStalled(long ruleId, String template, LocalDate today, int days,
                                      List<AlertPushService.Alert> collector) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                        "SELECT id, org_id, title, (CAST(:today AS date) - CAST(updated_at AS date)) AS stalled "
                                + "FROM assessment "
                                + "WHERE status = 'PENDING_REVIEW' "
                                + "AND CAST(updated_at AS date) <= CAST(:today AS date) - :days")
                .setParameter("today", today.toString())
                .setParameter("days", days)
                .getResultList();
        int n = 0;
        for (Object[] r : rows) {
            String msg = template
                    .replace("{标题}", nullSafe((String) r[2]))
                    .replace("{滞留天数}", String.valueOf(((Number) r[3]).intValue()));
            n += dispatch("ASSESSMENT", ((Number) r[0]).longValue(), "RULE_ASSESSMENT_STALLED",
                    "rule=" + ruleId, ((Number) r[1]).longValue(), msg, ruleId, collector);
        }
        return n;
    }

    /** 法规新采集：近 windowDays 天入库的采集条目。变量：{标题} {发布机构}。 */
    private int scanRegNew(long ruleId, String template, LocalDate today, int windowDays,
                           List<AlertPushService.Alert> collector) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                        "SELECT id, org_id, title, issuer FROM regulation_crawled "
                                + "WHERE CAST(fetched_at AS date) > CAST(:today AS date) - :win")
                .setParameter("today", today.toString())
                .setParameter("win", windowDays)
                .getResultList();
        int n = 0;
        for (Object[] r : rows) {
            String msg = template
                    .replace("{标题}", nullSafe((String) r[2]))
                    .replace("{发布机构}", nullSafe((String) r[3]));
            n += dispatch("REG_CRAWLED", ((Number) r[0]).longValue(), "RULE_REG_NEW",
                    "rule=" + ruleId, ((Number) r[1]).longValue(), msg, ruleId, collector);
        }
        return n;
    }

    /** KRI 触阈：每个 KRI 的最新一次测量为 WARNING/CRITICAL。变量：{指标} {数值} {单位} {级别}。 */
    private int scanKriBreach(long ruleId, String template, List<AlertPushService.Alert> collector) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                        "SELECT m.id, m.org_id, k.name, m.value, k.unit, m.status "
                                + "FROM (SELECT DISTINCT ON (kri_id) * FROM kri_measurement "
                                + "      ORDER BY kri_id, measured_at DESC, id DESC) m "
                                + "JOIN kri k ON k.id = m.kri_id "
                                + "WHERE m.status IN ('WARNING','CRITICAL')")
                .getResultList();
        int n = 0;
        for (Object[] r : rows) {
            String level = "CRITICAL".equals(r[5]) ? "严重" : "预警";
            String msg = template
                    .replace("{指标}", nullSafe((String) r[2]))
                    .replace("{数值}", r[3] == null ? "—" : String.valueOf(r[3]))
                    .replace("{单位}", nullSafe((String) r[4]))
                    .replace("{级别}", level);
            n += dispatch("KRI_MEASUREMENT", ((Number) r[0]).longValue(), "RULE_KRI_BREACH",
                    "rule=" + ruleId, ((Number) r[1]).longValue(), msg, ruleId, collector);
        }
        return n;
    }

    /** 幂等派发：唯一约束冲突（已产过）返回 0，仅真正新增才计数；新增的进 collector 供外推。 */
    private int dispatch(String objectType, long objectId, String eventType, String thresholdKey,
                         long orgId, String message, long ruleId, List<AlertPushService.Alert> collector) {
        int inserted = em.createNativeQuery(
                        "INSERT INTO reminder_dispatch_log"
                                + "(object_type, object_id, event_type, threshold_key, org_id, message, rule_id) "
                                + "VALUES (:ot, :oid, :et, :tk, :org, :msg, :rid) "
                                + "ON CONFLICT (object_type, object_id, event_type, threshold_key) DO NOTHING")
                .setParameter("ot", objectType)
                .setParameter("oid", objectId)
                .setParameter("et", eventType)
                .setParameter("tk", thresholdKey)
                .setParameter("org", orgId)
                .setParameter("msg", message)
                .setParameter("rid", ruleId)
                .executeUpdate();
        if (inserted == 1 && collector != null) {
            collector.add(new AlertPushService.Alert(orgId, message));
        }
        return inserted;
    }

    private static String nullSafe(String s) {
        return s == null ? "—" : s;
    }
}
