package com.mandao.grc.modules.workbench;

import com.mandao.grc.common.auth.CurrentUserContext;
import com.mandao.grc.common.isolation.IsolationContext;
import com.mandao.grc.modules.audit.management.RemediationOrderRepository;
import com.mandao.grc.modules.audit.management.RemediationStatus;
import com.mandao.grc.modules.regulatory.CompliancePlanItemRepository;
import com.mandao.grc.modules.regulatory.CompliancePlanItemStatus;
import com.mandao.grc.modules.regulatory.RegFilingRepository;
import com.mandao.grc.modules.regulatory.RegFilingStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 工作台服务（横切聚合·只读）：我的待办 + 通知中心。
 *
 * 待办 {@link #todos}：跨模块归并可见范围内待处理工作（未验证整改工单 / 未完成合规计划项 / 待报送）；
 * 数据源均为 RLS 受控表，靠切面注入 visible_orgs 自动按域裁剪。
 *
 * 通知 {@link #notifications}：调度内核派发的提醒（reminder_dispatch_log）。该表为内核内部表【未启 RLS】，
 * 故此处按 {@link IsolationContext} 当前可见组织显式过滤（org_id IN visible_orgs），避免跨域泄露。
 *
 * 设计依据：需求「我的待办 / 通知中心」、调度内核(D1-9 H-01)、D2-5。
 */
@Service
public class WorkbenchService {

    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 500;

    // ===== B28：通知分类与订阅偏好 =====
    /** event_type → 可订阅分类键。未列出的归 OTHER（可静音）。 */
    private static final java.util.Map<String, String> EVENT_CATEGORY = java.util.Map.ofEntries(
            java.util.Map.entry("RULE_REMEDIATION_OVERDUE", "REMEDIATION"),
            java.util.Map.entry("RULE_ASSESSMENT_STALLED", "ASSESSMENT"),
            java.util.Map.entry("ASSET_CHANGED", "ASSESSMENT"),
            java.util.Map.entry("RULE_REG_NEW", "REGULATION"),
            java.util.Map.entry("RULE_KRI_BREACH", "RISK"),
            java.util.Map.entry("EXT_AUDIT_PLAN_APPROACHING", "AUDIT"),
            java.util.Map.entry("PERIODIC_FILING_GENERATED", "FILING"));
    /** 法定时限红线类——不可静音（无论用户偏好如何都必须送达）。 */
    private static final java.util.Set<String> URGENT_EVENTS = java.util.Set.of(
            "REG_FILING_DUE", "MAJOR_INCIDENT_REPORT_DUE", "MLPS_REVIEW_DUE");

    @PersistenceContext
    private EntityManager em;

    private final RemediationOrderRepository remediationOrderRepository;
    private final CompliancePlanItemRepository compliancePlanItemRepository;
    private final RegFilingRepository regFilingRepository;
    private final TaskService taskService;
    private final RuntimeService runtimeService;
    private final com.mandao.grc.common.auth.AppUserRepository appUserRepository;

    public WorkbenchService(RemediationOrderRepository remediationOrderRepository,
                            CompliancePlanItemRepository compliancePlanItemRepository,
                            RegFilingRepository regFilingRepository,
                            TaskService taskService,
                            RuntimeService runtimeService,
                            com.mandao.grc.common.auth.AppUserRepository appUserRepository) {
        this.remediationOrderRepository = remediationOrderRepository;
        this.compliancePlanItemRepository = compliancePlanItemRepository;
        this.regFilingRepository = regFilingRepository;
        this.taskService = taskService;
        this.runtimeService = runtimeService;
        this.appUserRepository = appUserRepository;
    }

    // ===== B28：通知订阅偏好 CRUD + 过滤辅助 =====

    /** 当前登录人静音的分类集合（未登录/无偏好返回空集）。 */
    private java.util.Set<String> mutedCategoriesOfCurrentUser() {
        String username = com.mandao.grc.common.auth.CurrentUserContext.get();
        if (username == null) {
            return java.util.Set.of();
        }
        Long uid = appUserRepository.findByUsername(username).map(u -> u.getId()).orElse(null);
        if (uid == null) {
            return java.util.Set.of();
        }
        List<?> rows = em.createNativeQuery("SELECT muted_categories FROM notify_preference WHERE user_id = :uid")
                .setParameter("uid", uid).getResultList();
        if (rows.isEmpty() || rows.get(0) == null) {
            return java.util.Set.of();
        }
        String csv = (String) rows.get(0);
        if (csv.isBlank()) {
            return java.util.Set.of();
        }
        return java.util.Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isBlank())
                .collect(java.util.stream.Collectors.toSet());
    }

    /** 读当前登录人的订阅偏好（静音分类列表）。 */
    @Transactional(readOnly = true)
    public java.util.List<String> getMutedCategories() {
        return new java.util.ArrayList<>(mutedCategoriesOfCurrentUser());
    }

    /** 保存当前登录人的订阅偏好——法定时限红线分类被强制剔除（不可静音）。 */
    @Transactional
    public void setMutedCategories(java.util.List<String> categories) {
        String username = com.mandao.grc.common.auth.CurrentUserContext.get();
        if (username == null) {
            throw new IllegalStateException("未登录，无法保存通知偏好");
        }
        Long uid = appUserRepository.findByUsername(username).map(u -> u.getId())
                .orElseThrow(() -> new IllegalStateException("当前用户不存在：" + username));
        // 红线分类不允许静音（即便前端误传也剔除）
        String csv = categories == null ? "" : categories.stream()
                .map(String::trim).filter(s -> !s.isBlank() && !"URGENT".equals(s))
                .distinct().collect(java.util.stream.Collectors.joining(","));
        em.createNativeQuery("INSERT INTO notify_preference(user_id, muted_categories, updated_at) "
                        + "VALUES (:uid, :csv, now()) "
                        + "ON CONFLICT (user_id) DO UPDATE SET muted_categories = :csv, updated_at = now()")
                .setParameter("uid", uid).setParameter("csv", csv).executeUpdate();
    }

    /** 该 event_type 是否应对当前用户隐藏（命中静音分类且非红线）。 */
    private boolean isMuted(String eventType, java.util.Set<String> muted) {
        if (URGENT_EVENTS.contains(eventType)) {
            return false;  // 法定时限红线：永不静音
        }
        String category = EVENT_CATEGORY.getOrDefault(eventType, "OTHER");
        return muted.contains(category);
    }

    /** 我的待办：可见范围内待处理工作的统一聚合（RLS 自动按域裁剪）。 */
    @Transactional(readOnly = true)
    public List<TodoItem> todos() {
        List<TodoItem> out = new ArrayList<>();

        // 未验证的整改工单
        remediationOrderRepository.findAll().stream()
                .filter(o -> o.getStatus() != RemediationStatus.VERIFIED)
                .forEach(o -> out.add(new TodoItem("REMEDIATION", o.getId(),
                        "整改工单（发现 " + o.getFindingId() + "）", o.getDueDate(), o.getStatus().name())));

        // 未完成的合规计划项
        compliancePlanItemRepository.findAll().stream()
                .filter(i -> i.getStatus() != CompliancePlanItemStatus.DONE)
                .forEach(i -> out.add(new TodoItem("COMPLIANCE_ITEM", i.getId(),
                        i.getMatter(), i.getDueDate(), i.getStatus().name())));

        // 待报送（未报送/复核中）
        regFilingRepository.findAll().stream()
                .filter(f -> f.getStatus() == RegFilingStatus.TO_DRAFT
                        || f.getStatus() == RegFilingStatus.DRAFTING
                        || f.getStatus() == RegFilingStatus.PENDING_REVIEW)
                .forEach(f -> out.add(new TodoItem("REG_FILING", f.getId(),
                        f.getTitle(), f.getStatutoryDeadline(), f.getStatus().name())));

        return out;
    }

    /**
     * 我的审批待办（按登录人过滤）：当前登录人持有的角色 = Flowable 候选组，
     * 命中的待办审批任务即"分给我"。区别于 {@link #todos()} 的"按组织"，这是真正"按登录人"。
     */
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<MyApprovalItem> myApprovals() {
        String username = CurrentUserContext.get();
        if (username == null || username.isBlank()) {
            return List.of();
        }
        // 当前登录人有效角色码（user_role_org 受 RLS，本人在其组织内的授权可见）
        List<String> roleCodes = em.createNativeQuery(
                        "SELECT DISTINCT r.code FROM app_user u "
                                + "JOIN user_role_org uro ON uro.user_id = u.id AND uro.active = true "
                                + "JOIN role r ON r.id = uro.role_id WHERE u.username = :n")
                .setParameter("n", username).getResultList();

        List<MyApprovalItem> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String code : roleCodes) {
            for (Task t : taskService.createTaskQuery().taskCandidateGroup(code)
                    .orderByTaskCreateTime().asc().list()) {
                if (!seen.add(t.getId())) {
                    continue; // 同一任务可能匹配多个角色，去重
                }
                String bizType = null;
                Long bizId = null;
                String bk = businessKeyOf(t.getProcessInstanceId());
                if (bk != null && bk.contains(":")) {
                    String[] p = bk.split(":", 2);
                    bizType = p[0];
                    try {
                        bizId = Long.parseLong(p[1]);
                    } catch (NumberFormatException ignore) {
                        // 业务键非标准格式，bizId 保持 null
                    }
                }
                long created = t.getCreateTime() != null ? t.getCreateTime().getTime() : 0L;
                out.add(new MyApprovalItem(t.getId(), bizType, bizId, t.getName(), code, created));
            }
        }
        return out;
    }

    /** 取流程实例的 businessKey（"bizType:bizId"）。 */
    private String businessKeyOf(String processInstanceId) {
        ProcessInstance pi = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId).singleResult();
        return pi == null ? null : pi.getBusinessKey();
    }

    /**
     * 通知中心：可见组织范围内调度内核派发的提醒（新→旧）。
     *
     * 降噪（V41）：同一 (object_type, object_id, event_type) 的多次提醒（不同阈值日各产一条）
     * 合并为最新一条展示，mergedCount 记合并总数——避免同一事项在列表里刷屏。
     */
    @Transactional(readOnly = true)
    public List<NotificationView> notifications(Integer limit) {
        List<Long> orgs = IsolationContext.get();
        if (orgs == null || orgs.isEmpty()) {
            return List.of();
        }
        int lim = (limit == null || limit <= 0) ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);

        // reminder_dispatch_log 未启 RLS，显式按可见组织过滤；created_at 用 CAST 转 epoch 毫秒
        // （避免 Hibernate 原生 SQL 把 ::bigint 的 : 误判为命名参数）。
        Query q = em.createNativeQuery(
                "SELECT id, object_type, object_id, event_type, threshold_key, org_id, "
                        + "CAST(EXTRACT(EPOCH FROM created_at) * 1000 AS bigint) AS created_ms, "
                        + "cnt, read_by, "
                        + "COALESCE(CAST(EXTRACT(EPOCH FROM read_at) * 1000 AS bigint), 0) AS read_ms, "
                        + "message "
                        + "FROM (SELECT *, "
                        + "row_number() OVER (PARTITION BY object_type, object_id, event_type ORDER BY id DESC) AS rn, "
                        + "count(*) OVER (PARTITION BY object_type, object_id, event_type) AS cnt "
                        + "FROM reminder_dispatch_log WHERE org_id IN (:orgs)) t "
                        + "WHERE rn = 1 ORDER BY id DESC LIMIT " + lim);
        q.setParameter("orgs", orgs);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = q.getResultList();
        // B28：按当前用户订阅偏好过滤静音分类（法定时限红线不受影响）
        java.util.Set<String> muted = mutedCategoriesOfCurrentUser();
        return rows.stream()
                .filter(r -> !isMuted((String) r[3], muted))
                .map(r -> new NotificationView(
                        ((Number) r[0]).longValue(),
                        (String) r[1],
                        ((Number) r[2]).longValue(),
                        (String) r[3],
                        (String) r[4],
                        ((Number) r[5]).longValue(),
                        ((Number) r[6]).longValue(),
                        ((Number) r[7]).longValue(),
                        (String) r[8],
                        ((Number) r[9]).longValue(),
                        (String) r[10]))
                .toList();
    }

    /**
     * 通知回执（V41）：确认收到某条提醒（写 read_by/read_at）。
     * 仅能确认可见组织内的提醒；重复确认幂等（保留首次回执人）。
     */
    @Transactional
    public void ackNotification(Long id, String actor) {
        List<Long> orgs = IsolationContext.get();
        if (orgs == null || orgs.isEmpty()) {
            return;
        }
        em.createNativeQuery(
                        "UPDATE reminder_dispatch_log SET read_by = :actor, read_at = now() "
                                + "WHERE id = :id AND org_id IN (:orgs) AND read_by IS NULL")
                .setParameter("actor", actor)
                .setParameter("id", id)
                .setParameter("orgs", orgs)
                .executeUpdate();
    }

    /** 简报条目：某事件类型近 N 天的提醒次数与未回执数。 */
    public record DigestRow(String eventType, long total, long unread) {
    }

    /**
     * 定期简报（V41）：近 N 天可见组织范围内的提醒按事件类型聚合（总数/未回执数），
     * 供通知中心「定期简报」卡展示；管理层文字版简报由 AI 生成材料（/api/ai/generate）承接。
     */
    @Transactional(readOnly = true)
    public List<DigestRow> digest(Integer days) {
        List<Long> orgs = IsolationContext.get();
        if (orgs == null || orgs.isEmpty()) {
            return List.of();
        }
        int d = (days == null || days <= 0) ? 7 : Math.min(days, 90);
        Query q = em.createNativeQuery(
                "SELECT event_type, count(*), count(*) FILTER (WHERE read_by IS NULL) "
                        + "FROM reminder_dispatch_log "
                        + "WHERE org_id IN (:orgs) AND created_at > now() - CAST(:days || ' days' AS interval) "
                        + "GROUP BY event_type ORDER BY count(*) DESC");
        q.setParameter("orgs", orgs);
        q.setParameter("days", String.valueOf(d));

        @SuppressWarnings("unchecked")
        List<Object[]> rows = q.getResultList();
        return rows.stream()
                .map(r -> new DigestRow((String) r[0],
                        ((Number) r[1]).longValue(),
                        ((Number) r[2]).longValue()))
                .toList();
    }
}
