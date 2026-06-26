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

    @PersistenceContext
    private EntityManager em;

    private final RemediationOrderRepository remediationOrderRepository;
    private final CompliancePlanItemRepository compliancePlanItemRepository;
    private final RegFilingRepository regFilingRepository;
    private final TaskService taskService;
    private final RuntimeService runtimeService;

    public WorkbenchService(RemediationOrderRepository remediationOrderRepository,
                            CompliancePlanItemRepository compliancePlanItemRepository,
                            RegFilingRepository regFilingRepository,
                            TaskService taskService,
                            RuntimeService runtimeService) {
        this.remediationOrderRepository = remediationOrderRepository;
        this.compliancePlanItemRepository = compliancePlanItemRepository;
        this.regFilingRepository = regFilingRepository;
        this.taskService = taskService;
        this.runtimeService = runtimeService;
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

    /** 通知中心：可见组织范围内调度内核派发的提醒（新→旧）。 */
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
                "SELECT object_type, object_id, event_type, threshold_key, org_id, "
                        + "CAST(EXTRACT(EPOCH FROM created_at) * 1000 AS bigint) AS created_ms "
                        + "FROM reminder_dispatch_log WHERE org_id IN (:orgs) ORDER BY id DESC LIMIT " + lim);
        q.setParameter("orgs", orgs);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = q.getResultList();
        return rows.stream()
                .map(r -> new NotificationView(
                        (String) r[0],
                        ((Number) r[1]).longValue(),
                        (String) r[2],
                        (String) r[3],
                        ((Number) r[4]).longValue(),
                        ((Number) r[5]).longValue()))
                .toList();
    }
}
