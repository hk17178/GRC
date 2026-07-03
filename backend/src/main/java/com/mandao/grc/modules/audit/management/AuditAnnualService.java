package com.mandao.grc.modules.audit.management;

import com.mandao.grc.modules.audit.HashChainService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 年度审计计划 + 后续审计服务（V52 · A3）。
 *
 * 年度层：年度计划（一组织一年一份）→ 对象清单（风险排序/排期）→ 批准冻结清单 →
 * 逐项转单项审计计划（回填 plan_id，避免重复立项）。
 * follow-up：对已关闭计划发起后续审计，新计划 follow_up_of 关联原计划，验证原发现整改有效性。
 */
@Service
public class AuditAnnualService {

    private final AuditAnnualPlanRepository annualRepo;
    private final AuditAnnualItemRepository itemRepo;
    private final AuditPlanRepository planRepo;
    private final AuditPlanService planService;
    private final HashChainService hashChainService;

    public AuditAnnualService(AuditAnnualPlanRepository annualRepo, AuditAnnualItemRepository itemRepo,
                              AuditPlanRepository planRepo, AuditPlanService planService,
                              HashChainService hashChainService) {
        this.annualRepo = annualRepo;
        this.itemRepo = itemRepo;
        this.planRepo = planRepo;
        this.planService = planService;
        this.hashChainService = hashChainService;
    }

    @Transactional(readOnly = true)
    public List<AuditAnnualPlan> list() {
        return annualRepo.findAllByOrderByYearDesc();
    }

    @Transactional(readOnly = true)
    public List<AuditAnnualItem> listItems(Long annualId) {
        get(annualId); // 可见性校验
        return itemRepo.findByAnnualIdOrderByRiskRankAscQuarterAsc(annualId);
    }

    /** 新建年度计划（DRAFT；同组织同年度唯一，由 uk 约束兜底）。 */
    @Transactional
    public AuditAnnualPlan create(Long orgId, Integer year, String title, String actor) {
        AuditAnnualPlan saved = annualRepo.save(new AuditAnnualPlan(orgId, year,
                title == null || title.isBlank() ? year + " 年度内部审计计划" : title, actor));
        hashChainService.append(orgId, "AUDIT_ANNUAL_CREATE", actor, "AUDIT_ANNUAL:" + saved.getId(),
                "新建年度审计计划 " + year);
        return saved;
    }

    /** 追加审计对象（仅 DRAFT 年度计划可改）。 */
    @Transactional
    public AuditAnnualItem addItem(Long annualId, String target, Integer riskRank, String quarter,
                                   String note, String actor) {
        AuditAnnualPlan annual = get(annualId);
        if (!"DRAFT".equals(annual.getStatus())) {
            throw new IllegalStateException("年度计划已批准，对象清单冻结（转单项计划不受限）");
        }
        AuditAnnualItem saved = itemRepo.save(
                new AuditAnnualItem(annual.getOrgId(), annualId, target, riskRank, quarter, note));
        hashChainService.append(annual.getOrgId(), "AUDIT_ANNUAL_ITEM", actor, "AUDIT_ANNUAL:" + annualId,
                "纳入审计对象「" + target + "」风险序=" + saved.getRiskRank() + " 排期=" + saved.getQuarter());
        return saved;
    }

    /** 批准年度计划：DRAFT → APPROVED（对象清单冻结）。 */
    @Transactional
    public AuditAnnualPlan approve(Long annualId, String actor) {
        AuditAnnualPlan annual = get(annualId);
        if (!"DRAFT".equals(annual.getStatus())) {
            throw new IllegalStateException("年度计划已批准");
        }
        if (itemRepo.findByAnnualIdOrderByRiskRankAscQuarterAsc(annualId).isEmpty()) {
            throw new IllegalStateException("对象清单为空，不可批准");
        }
        annual.approve(actor);
        AuditAnnualPlan saved = annualRepo.save(annual);
        hashChainService.append(annual.getOrgId(), "AUDIT_ANNUAL_APPROVE", actor,
                "AUDIT_ANNUAL:" + annualId, "批准年度审计计划");
        return saved;
    }

    /** 对象转单项审计计划（回填 plan_id 防重复立项；仅已批准的年度计划可立项）。 */
    @Transactional
    public AuditAnnualItem toPlan(Long itemId, LocalDate planStartDate, String actor) {
        AuditAnnualItem item = itemRepo.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("年度计划条目不存在或不可见：id=" + itemId));
        AuditAnnualPlan annual = get(item.getAnnualId());
        if (!"APPROVED".equals(annual.getStatus())) {
            throw new IllegalStateException("年度计划未批准，不可立项");
        }
        if (item.getPlanId() != null) {
            throw new IllegalStateException("该对象已立项（AP-" + item.getPlanId() + "），不可重复立项");
        }
        AuditPlan plan = planService.create(item.getOrgId(),
                annual.getYear() + " " + item.getTarget() + " 审计", AuditType.INTERNAL,
                planStartDate == null ? LocalDate.now() : planStartDate, actor);
        item.linkPlan(plan.getId());
        AuditAnnualItem saved = itemRepo.save(item);
        hashChainService.append(item.getOrgId(), "AUDIT_ANNUAL_TO_PLAN", actor,
                "AUDIT_ANNUAL:" + item.getAnnualId(), "对象「" + item.getTarget() + "」立项 AP-" + plan.getId());
        return saved;
    }

    /** 发起后续审计（follow-up）：验证原计划发现的整改有效性；原计划须已关闭。 */
    @Transactional
    public AuditPlan followUp(Long planId, LocalDate startDate, String actor) {
        AuditPlan origin = planRepo.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("审计计划不存在或不可见：id=" + planId));
        if (origin.getStatus() != AuditPlanStatus.CLOSED) {
            throw new IllegalStateException("仅已关闭(CLOSED)的计划可发起后续审计，当前：" + origin.getStatus());
        }
        AuditPlan follow = planService.create(origin.getOrgId(), "后续审计 · " + origin.getTitle(),
                origin.getAuditType(), startDate == null ? LocalDate.now() : startDate, actor);
        // 回查原计划标记（entity 包级 setter，须再存一次）
        follow.setFollowUpOf(planId);
        AuditPlan saved = planRepo.save(follow);
        hashChainService.append(origin.getOrgId(), "AUDIT_FOLLOW_UP", actor, "AUDIT_PLAN:" + saved.getId(),
                "发起后续审计，验证 AP-" + planId + " 整改有效性");
        return saved;
    }

    private AuditAnnualPlan get(Long id) {
        return annualRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("年度审计计划不存在或不可见：id=" + id));
    }
}
