package com.mandao.grc.modules.regulatory;

import com.mandao.grc.modules.audit.HashChainService;
import com.mandao.grc.modules.workflow.ApprovalDecision;
import com.mandao.grc.modules.workflow.WorkflowService;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 报送日历业务服务（M11 监管事项；报送生命周期）。
 *
 * 隔离：方法 @Transactional 且位于 com.mandao.grc.modules 包 → {@link com.mandao.grc.common.isolation.OrgScopeAspect}
 * 在事务内自动注入 app.visible_orgs，RLS 裁剪数据并校验写入（WITH CHECK，V8 已为 reg_filing 建）。
 *
 * 状态机：TO_DRAFT → DRAFTING → SUBMITTED → CLOSED；非法流转抛 {@link IllegalStateException}。
 *
 * 留痕：每次流转后 {@link HashChainService#append} 写入按 org 分链的防篡改哈希链（entity="REG_FILING:{id}"）。
 *
 * 调度兼容：新建报送写库后，reminder_days 由 V8 库级 DEFAULT '{15,10}' 兜底，故 ExpiryScanService 可据
 * statutory_deadline + reminder_days 产 REG_FILING_DUE（法定时限预警）；本 status 不影响调度。
 *
 * 设计依据：需求文档 M11 监管事项（报送日历）、D1-2 §23、D2-5、DM-5 状态机基线。
 */
@Service
public class RegFilingService {

    /** 报送复核审批的业务类型与审批候选组（Flowable）。 */
    public static final String FILING_BIZ_TYPE = "REG_FILING";
    public static final String FILING_APPROVER_GROUP = "REG_FILING_APPROVER";

    private final RegFilingRepository repository;
    private final HashChainService hashChainService;
    private final WorkflowService workflowService;

    public RegFilingService(RegFilingRepository repository, HashChainService hashChainService,
                            WorkflowService workflowService) {
        this.repository = repository;
        this.hashChainService = hashChainService;
        this.workflowService = workflowService;
    }

    /** 列出可见组织范围内的报送事项（无 org 过滤，靠切面 + RLS）。 */
    @Transactional(readOnly = true)
    public List<RegFiling> list() {
        return repository.findAll();
    }

    /** 按 id 取报送事项（仅能取到可见组织内的；不可见则视为不存在）。 */
    @Transactional(readOnly = true)
    public RegFiling get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("报送事项不存在或不可见：id=" + id));
    }

    /**
     * 新建报送事项（TO_DRAFT 态）。
     *
     * @param orgId             归属组织（须在 visible_orgs 内，否则 RLS WITH CHECK 拒绝写入）
     * @param statutoryDeadline 法定报送时限（调度据此 + reminder_days 产法定时限预警）
     */
    @Transactional
    public RegFiling create(Long orgId, String title, String regulator,
                            LocalDate statutoryDeadline, String actor) {
        RegFiling f = new RegFiling(orgId, title, regulator, statutoryDeadline);
        RegFiling saved = repository.save(f);
        appendLog(saved, "REG_FILING_CREATE", actor,
                "新建报送事项 title=" + title + " regulator=" + regulator + " deadline=" + statutoryDeadline);
        return saved;
    }

    /** 开始起草：TO_DRAFT → DRAFTING。 */
    @Transactional
    public RegFiling prepare(Long id, String actor) {
        RegFiling f = get(id);
        transition(f, RegFilingStatus.TO_DRAFT, RegFilingStatus.DRAFTING);
        RegFiling saved = repository.save(f);
        appendLog(saved, "REG_FILING_PREPARE", actor, "开始起草报送材料");
        return saved;
    }

    /**
     * 提交内部复核（A5 报送审批化）：DRAFTING → PENDING_REVIEW，并启动 Flowable 复核审批。
     * 报送材料须经内部复核通过后方可正式报送监管机构。
     */
    @Transactional
    public RegFiling submitForReview(Long id, String actor) {
        RegFiling f = get(id);
        transition(f, RegFilingStatus.DRAFTING, RegFilingStatus.PENDING_REVIEW);
        RegFiling saved = repository.save(f);
        appendLog(saved, "REG_FILING_SUBMIT_REVIEW", actor, "提交报送复核");
        workflowService.submit(FILING_BIZ_TYPE, saved.getId(), saved.getOrgId(), FILING_APPROVER_GROUP, actor);
        return saved;
    }

    /**
     * 复核处置（A5）：定位该报送进行中的复核任务 → {@link WorkflowService#decide} 完成 →
     * 通过则 PENDING_REVIEW → SUBMITTED（正式报送）；驳回则 PENDING_REVIEW → DRAFTING（退回起草）。同事务原子 + 留痕。
     */
    @Transactional
    public RegFiling decideSubmit(Long id, ApprovalDecision decision, String approver, String comment) {
        RegFiling f = get(id);
        if (f.getStatus() != RegFilingStatus.PENDING_REVIEW) {
            throw new IllegalStateException("仅复核中(PENDING_REVIEW)的报送可处置，当前状态：" + f.getStatus());
        }
        Task task = workflowService.activeTask(FILING_BIZ_TYPE, id);
        if (task == null) {
            throw new IllegalStateException("报送 id=" + id + " 无进行中的复核审批任务");
        }
        workflowService.decide(task.getId(), decision, approver, comment);
        if (decision == ApprovalDecision.APPROVED) {
            f.setStatus(RegFilingStatus.SUBMITTED);
            RegFiling saved = repository.save(f);
            appendLog(saved, "REG_FILING_SUBMIT", approver, "复核通过，已提交监管机构");
            return saved;
        } else {
            f.setStatus(RegFilingStatus.DRAFTING);
            RegFiling saved = repository.save(f);
            appendLog(saved, "REG_FILING_REVIEW_REJECT", approver,
                    "复核驳回退回起草：" + (comment == null ? "" : comment));
            return saved;
        }
    }

    /** 了结：SUBMITTED → CLOSED（终态）。 */
    @Transactional
    public RegFiling close(Long id, String actor) {
        RegFiling f = get(id);
        transition(f, RegFilingStatus.SUBMITTED, RegFilingStatus.CLOSED);
        RegFiling saved = repository.save(f);
        appendLog(saved, "REG_FILING_CLOSE", actor, "报送了结");
        return saved;
    }

    // ---------- 内部辅助 ----------

    private void transition(RegFiling f, RegFilingStatus expectedFrom, RegFilingStatus to) {
        if (f.getStatus() != expectedFrom) {
            throw new IllegalStateException(
                    "非法状态流转：报送事项 id=" + f.getId()
                            + " 当前状态=" + f.getStatus()
                            + "，仅允许从 " + expectedFrom + " 流转到 " + to);
        }
        f.setStatus(to);
    }

    private void appendLog(RegFiling f, String action, String actor, String detail) {
        hashChainService.append(f.getOrgId(), action, actor, "REG_FILING:" + f.getId(), detail);
    }
}
