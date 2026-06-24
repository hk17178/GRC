package com.mandao.grc.modules.regulatory;

import com.mandao.grc.modules.audit.HashChainService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 年度合规计划业务服务（M11 监管事项·年度计划）。
 *
 * 隔离/留痕范式同其它模块：方法 @Transactional 且位于 com.mandao.grc.modules 包，切面注入 visible_orgs，
 * RLS 裁剪 + 写校验；关键操作经 {@link HashChainService#append} 入按 org 分链的哈希链。
 *
 * 计划状态机 DRAFT → ACTIVE → CLOSED；仅 DRAFT 可增改计划项、须有项方可下发(ACTIVE)。
 *
 * 设计依据：需求文档 M11 监管事项（年度合规计划）、D2-5。
 */
@Service
public class CompliancePlanService {

    private final CompliancePlanRepository planRepository;
    private final CompliancePlanItemRepository itemRepository;
    private final HashChainService hashChainService;

    public CompliancePlanService(CompliancePlanRepository planRepository,
                                 CompliancePlanItemRepository itemRepository,
                                 HashChainService hashChainService) {
        this.planRepository = planRepository;
        this.itemRepository = itemRepository;
        this.hashChainService = hashChainService;
    }

    @Transactional(readOnly = true)
    public List<CompliancePlan> list() {
        return planRepository.findAll();
    }

    @Transactional(readOnly = true)
    public CompliancePlan get(Long id) {
        return planRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("年度合规计划不存在或不可见：id=" + id));
    }

    @Transactional(readOnly = true)
    public List<CompliancePlanItem> listItems(Long planId) {
        get(planId); // 可见性校验
        return itemRepository.findByPlanIdOrderBySeqAsc(planId);
    }

    /** 新建年度计划（DRAFT）。 */
    @Transactional
    public CompliancePlan create(Long orgId, Integer year, String title, String owner, String actor) {
        CompliancePlan saved = planRepository.save(new CompliancePlan(orgId, year, title, owner));
        hashChainService.append(orgId, "COMPLIANCE_PLAN_CREATE", actor, "COMPLIANCE_PLAN:" + saved.getId(),
                "新建年度合规计划 year=" + year + " title=" + title);
        return saved;
    }

    /** 追加计划项（仅 DRAFT 计划可改），序号自动顺延。 */
    @Transactional
    public CompliancePlanItem addItem(Long planId, String matter, String ownerDept,
                                      LocalDate dueDate, String actor) {
        CompliancePlan p = get(planId);
        if (p.getStatus() != CompliancePlanStatus.DRAFT) {
            throw new IllegalStateException("仅草稿(DRAFT)计划可增改计划项，当前状态：" + p.getStatus());
        }
        int seq = itemRepository.findByPlanIdOrderBySeqAsc(planId).size() + 1;
        CompliancePlanItem saved = itemRepository.save(
                new CompliancePlanItem(p.getOrgId(), planId, seq, matter, ownerDept, dueDate));
        hashChainService.append(p.getOrgId(), "COMPLIANCE_PLAN_ADD_ITEM", actor, "COMPLIANCE_PLAN:" + planId,
                "追加计划项 seq=" + seq + " 责任部门=" + ownerDept);
        return saved;
    }

    /** 下发执行：DRAFT → ACTIVE（须至少 1 条计划项）。 */
    @Transactional
    public CompliancePlan activate(Long planId, String actor) {
        CompliancePlan p = get(planId);
        if (p.getStatus() != CompliancePlanStatus.DRAFT) {
            throw new IllegalStateException("仅草稿(DRAFT)计划可下发，当前状态：" + p.getStatus());
        }
        if (itemRepository.findByPlanIdOrderBySeqAsc(planId).isEmpty()) {
            throw new IllegalStateException("计划无计划项，不可下发");
        }
        p.setStatus(CompliancePlanStatus.ACTIVE);
        CompliancePlan saved = planRepository.save(p);
        hashChainService.append(p.getOrgId(), "COMPLIANCE_PLAN_ACTIVATE", actor, "COMPLIANCE_PLAN:" + planId, "下发执行");
        return saved;
    }

    /** 收口：ACTIVE → CLOSED（终态）。 */
    @Transactional
    public CompliancePlan close(Long planId, String actor) {
        CompliancePlan p = get(planId);
        if (p.getStatus() != CompliancePlanStatus.ACTIVE) {
            throw new IllegalStateException("仅执行中(ACTIVE)计划可收口，当前状态：" + p.getStatus());
        }
        p.setStatus(CompliancePlanStatus.CLOSED);
        CompliancePlan saved = planRepository.save(p);
        hashChainService.append(p.getOrgId(), "COMPLIANCE_PLAN_CLOSE", actor, "COMPLIANCE_PLAN:" + planId, "年度计划收口");
        return saved;
    }

    /** 更新计划项进度状态并留痕。 */
    @Transactional
    public CompliancePlanItem updateItemStatus(Long itemId, CompliancePlanItemStatus status, String actor) {
        CompliancePlanItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("计划项不存在或不可见：id=" + itemId));
        item.setStatus(status);
        CompliancePlanItem saved = itemRepository.save(item);
        hashChainService.append(item.getOrgId(), "COMPLIANCE_ITEM_UPDATE", actor,
                "COMPLIANCE_PLAN:" + item.getPlanId(), "计划项 seq=" + item.getSeq() + " 进度=" + status);
        return saved;
    }
}
