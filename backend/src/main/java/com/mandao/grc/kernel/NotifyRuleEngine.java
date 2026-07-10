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

    /** §九 接线二：新告警消费本组织命中的通知场景，生成场景通知（供升级链运行器）。 */
    @org.springframework.beans.factory.annotation.Autowired
    private SceneNotifyConsumer sceneNotifyConsumer;

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
        // 架构治理包 A26：会话可见域走 set_config 参数化（防注入样板）
        VisibleOrgsSql.setAllOrgs(em);

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
                    case "REG_CHANGE" -> scanRegChange(ruleId, template, today, Math.max(days, 1), collector);
                    case "KRI_BREACH" -> scanKriBreach(ruleId, template, collector);
                    case "ASSESSMENT_UPCOMING" -> scanAssessmentUpcoming(ruleId, template, today, Math.max(days, 1), collector);
                    case "AUDIT_PLAN_UPCOMING" -> scanAuditUpcoming(ruleId, template, today, Math.max(days, 1), collector);
                    case "ACCOUNT_LOCKED" -> scanAccountLocked(ruleId, template, today, collector);
                    case "UAR_OVERDUE" -> scanUarOverdue(ruleId, template, today, Math.max(days, 7), collector);
                    case "COMPLIANCE_DIGEST" -> scanComplianceDigest(ruleId, template, today, collector);
                    default -> 0; // 未知数据源：跳过（旧格式或手工误配），不让单条脏规则拖垮整轮
                    // 注：制度复审/资质证书/监管报送/等保测评 等到期提醒由 ExpiryScanService 系统级统一产出，
                    //     不在此重复扫描以免双发（详见通知中心「已内置到期提醒」说明）。
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
                                + "WHERE CAST(fetched_at AS date) > CAST(:today AS date) - :win "
                                + "ORDER BY org_id, id DESC")
                .setParameter("today", today.toString())
                .setParameter("win", windowDays)
                .getResultList();
        // B35 同批合并降噪：一次抓取 20 条不再产 20 条提醒，按 org 汇总为一条摘要
        // （"本次新增 N 条法规：标题1、标题2…"），threshold_key 用当日 → 同日同 org 一条。
        java.util.Map<Long, java.util.List<String>> byOrg = new java.util.LinkedHashMap<>();
        for (Object[] r : rows) {
            long orgId = ((Number) r[1]).longValue();
            String title = nullSafe((String) r[2]);
            String issuer = nullSafe((String) r[3]);
            byOrg.computeIfAbsent(orgId, k -> new java.util.ArrayList<>())
                    .add(title + (issuer.isBlank() ? "" : "（" + issuer + "）"));
        }
        int n = 0;
        for (var e : byOrg.entrySet()) {
            java.util.List<String> titles = e.getValue();
            int total = titles.size();
            // 摘要最多列前 5 条标题，其余以"等 M 条"收口；模板含 {条数}/{标题列表} 占位则替换，否则用默认摘要
            String preview = String.join("、", titles.subList(0, Math.min(5, total)));
            String suffix = total > 5 ? "等 " + total + " 条" : "";
            String digest = template.contains("{条数}") || template.contains("{标题列表}")
                    ? template.replace("{条数}", String.valueOf(total)).replace("{标题列表}", preview + suffix)
                    : "本次新增 " + total + " 条法规：" + preview + suffix;
            n += dispatch("REG_CRAWLED_BATCH", e.getKey(), "RULE_REG_NEW",
                    today.toString(), e.getKey(), digest, ruleId, collector);
        }
        return n;
    }

    /** 风险评估临近开始：start_date 在未来 windowDays 天内且尚未启动（DRAFT）。变量：{标题} {剩余天数} {开始日}。 */
    private int scanAssessmentUpcoming(long ruleId, String template, LocalDate today, int windowDays,
                                       List<AlertPushService.Alert> collector) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                        "SELECT id, org_id, title, start_date, (start_date - CAST(:today AS date)) AS remain "
                                + "FROM assessment "
                                + "WHERE status = 'DRAFT' AND start_date IS NOT NULL "
                                + "AND start_date >= CAST(:today AS date) "
                                + "AND start_date <= CAST(:today AS date) + :win")
                .setParameter("today", today.toString())
                .setParameter("win", windowDays)
                .getResultList();
        int n = 0;
        for (Object[] r : rows) {
            String startDate = String.valueOf(r[3]);
            String msg = template
                    .replace("{标题}", nullSafe((String) r[2]))
                    .replace("{剩余天数}", String.valueOf(((Number) r[4]).intValue()))
                    .replace("{开始日}", startDate);
            // threshold_key 用开始日：改期才重新提醒，同一计划同一开始日只提醒一次
            n += dispatch("ASSESSMENT", ((Number) r[0]).longValue(), "RULE_ASSESSMENT_UPCOMING",
                    startDate, ((Number) r[1]).longValue(), msg, ruleId, collector);
        }
        return n;
    }

    /** 审计计划临近开始：plan_start_date 在未来 windowDays 天内且状态 PLANNED（含内/外审）。变量：{标题} {类型} {剩余天数} {开始日}。 */
    private int scanAuditUpcoming(long ruleId, String template, LocalDate today, int windowDays,
                                  List<AlertPushService.Alert> collector) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                        "SELECT id, org_id, title, audit_type, plan_start_date, "
                                + "(plan_start_date - CAST(:today AS date)) AS remain "
                                + "FROM audit_plan "
                                + "WHERE status = 'PLANNED' AND plan_start_date IS NOT NULL "
                                + "AND plan_start_date >= CAST(:today AS date) "
                                + "AND plan_start_date <= CAST(:today AS date) + :win")
                .setParameter("today", today.toString())
                .setParameter("win", windowDays)
                .getResultList();
        int n = 0;
        for (Object[] r : rows) {
            String typeCn = "INTERNAL".equals(r[3]) ? "内部审计" : "外部审计";
            String startDate = String.valueOf(r[4]);
            String msg = template
                    .replace("{标题}", nullSafe((String) r[2]))
                    .replace("{类型}", typeCn)
                    .replace("{剩余天数}", String.valueOf(((Number) r[5]).intValue()))
                    .replace("{开始日}", startDate);
            n += dispatch("AUDIT_PLAN", ((Number) r[0]).longValue(), "RULE_AUDIT_UPCOMING",
                    startDate, ((Number) r[1]).longValue(), msg, ruleId, collector);
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

    /** 法规变更影响预警（D1）：近 windowDays 天登记的法规变更，提示关联制度需重评。
     *  变量：{标题} {变更类型} {制度数} {说明}。变更登记时 RegulationService 已把关联映射置需重评。 */
    private int scanRegChange(long ruleId, String template, LocalDate today, int windowDays,
                              List<AlertPushService.Alert> collector) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                        "SELECT c.id, c.org_id, r.title, c.change_type, c.description, "
                                + "(SELECT COUNT(*) FROM regulation_policy_map m WHERE m.regulation_id = c.regulation_id) AS maps "
                                + "FROM regulation_change c JOIN regulation r ON r.id = c.regulation_id "
                                + "WHERE COALESCE(c.change_date, CAST(c.created_at AS date)) > CAST(:today AS date) - :win "
                                + "ORDER BY c.org_id, c.id DESC")
                .setParameter("today", today.toString())
                .setParameter("win", windowDays)
                .getResultList();
        int n = 0;
        for (Object[] r : rows) {
            int maps = ((Number) r[5]).intValue();
            String msg = template
                    .replace("{标题}", nullSafe((String) r[2]))
                    .replace("{变更类型}", changeTypeCn((String) r[3]))
                    .replace("{制度数}", String.valueOf(maps))
                    .replace("{说明}", nullSafe((String) r[4]));
            n += dispatch("REGULATION_CHANGE", ((Number) r[0]).longValue(), "RULE_REG_CHANGE",
                    "rule=" + ruleId, ((Number) r[1]).longValue(), msg, ruleId, collector);
        }
        return n;
    }

    /** 变更类型中文化（ENACTED/AMENDED/ABOLISHED）。 */
    private static String changeTypeCn(String t) {
        if (t == null) {
            return "变更";
        }
        return switch (t) {
            case "ENACTED" -> "新订";
            case "AMENDED" -> "修订";
            case "ABOLISHED" -> "废止";
            default -> t;
        };
    }

    /** 账号锁定（D3）：当前处于锁定期（locked_until 在未来）的账号。变量：{账号} {解锁时间}。
     *  app_user 为平台级账号表（无 org 锚点），统一归集团(org 1)派发，供安全管理员感知。 */
    private int scanAccountLocked(long ruleId, String template, LocalDate today,
                                  List<AlertPushService.Alert> collector) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                        "SELECT id, username, locked_until FROM app_user "
                                + "WHERE locked_until IS NOT NULL AND locked_until > now()")
                .getResultList();
        int n = 0;
        for (Object[] r : rows) {
            String until = r[2] == null ? "—" : r[2].toString();
            String msg = template
                    .replace("{账号}", nullSafe((String) r[1]))
                    .replace("{解锁时间}", until);
            // threshold_key 含本次解锁时间 → 同一次锁定只提醒一次；再次触发锁定（新 locked_until）会再提醒
            n += dispatch("APP_USER", ((Number) r[0]).longValue(), "RULE_ACCOUNT_LOCKED",
                    "lock=" + until, 1L, msg, ruleId, collector);
        }
        return n;
    }

    /** 访问复核超期（D3）：OPEN 的 UAR 距创建已超过 days 天仍未完成。变量：{周期} {超期天数} {审阅人}。 */
    private int scanUarOverdue(long ruleId, String template, LocalDate today, int days,
                              List<AlertPushService.Alert> collector) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                        "SELECT id, org_id, period, reviewer, "
                                + "(CAST(:today AS date) - CAST(created_at AS date)) AS age "
                                + "FROM access_review "
                                + "WHERE status = 'OPEN' AND CAST(created_at AS date) <= CAST(:today AS date) - :days")
                .setParameter("today", today.toString())
                .setParameter("days", days)
                .getResultList();
        int n = 0;
        for (Object[] r : rows) {
            int age = ((Number) r[4]).intValue();
            String msg = template
                    .replace("{周期}", nullSafe((String) r[2]))
                    .replace("{超期天数}", String.valueOf(age))
                    .replace("{审阅人}", nullSafe((String) r[3]));
            n += dispatch("ACCESS_REVIEW", ((Number) r[0]).longValue(), "RULE_UAR_OVERDUE",
                    "rule=" + ruleId, ((Number) r[1]).longValue(), msg, ruleId, collector);
        }
        return n;
    }

    /** 周期性合规简报（D4）：把每组织的 整改逾期 / KRI 严重 / 待复审制度 汇总为一条摘要，
     *  按 ISO 周去重（同周一条）；三项全 0 的组织不打扰。变量：{整改逾期} {KRI严重} {待复审制度} {周}。 */
    private int scanComplianceDigest(long ruleId, String template, LocalDate today,
                                     List<AlertPushService.Alert> collector) {
        java.time.temporal.WeekFields wf = java.time.temporal.WeekFields.ISO;
        String isoWeek = today.get(wf.weekBasedYear()) + "-W"
                + String.format("%02d", today.get(wf.weekOfWeekBasedYear()));
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                        "SELECT o.id, "
                                + "(SELECT COUNT(*) FROM remediation_order x WHERE x.org_id = o.id "
                                + "   AND x.status IN ('PENDING','IN_PROGRESS') AND x.due_date < CAST(:today AS date)) AS overdue, "
                                + "(SELECT COUNT(*) FROM (SELECT DISTINCT ON (kri_id) status, org_id FROM kri_measurement "
                                + "   ORDER BY kri_id, measured_at DESC, id DESC) k WHERE k.org_id = o.id AND k.status = 'CRITICAL') AS kri, "
                                + "(SELECT COUNT(*) FROM regulation_policy_map m WHERE m.org_id = o.id AND m.assess_stale = TRUE) AS reassess "
                                + "FROM org o")
                .setParameter("today", today.toString())
                .getResultList();
        int n = 0;
        for (Object[] r : rows) {
            long orgId = ((Number) r[0]).longValue();
            int overdue = ((Number) r[1]).intValue();
            int kri = ((Number) r[2]).intValue();
            int reassess = ((Number) r[3]).intValue();
            if (overdue == 0 && kri == 0 && reassess == 0) {
                continue; // 无异常项的组织不打扰
            }
            String msg = template
                    .replace("{整改逾期}", String.valueOf(overdue))
                    .replace("{KRI严重}", String.valueOf(kri))
                    .replace("{待复审制度}", String.valueOf(reassess))
                    .replace("{周}", isoWeek);
            n += dispatch("COMPLIANCE_DIGEST", orgId, "RULE_COMPLIANCE_DIGEST",
                    isoWeek, orgId, msg, ruleId, collector);
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
        if (inserted == 1) {
            if (collector != null) {
                collector.add(new AlertPushService.Alert(orgId, message));
            }
            // §九 接线二：只对真正新增的告警消费场景（幂等），跨子公司永不外溢（consume 显式按 org 过滤）
            sceneNotifyConsumer.consume(orgId, eventType, objectType, objectId, message);
        }
        return inserted;
    }

    private static String nullSafe(String s) {
        return s == null ? "—" : s;
    }
}
