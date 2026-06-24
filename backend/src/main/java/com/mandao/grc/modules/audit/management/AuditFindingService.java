package com.mandao.grc.modules.audit.management;

import com.mandao.grc.modules.audit.HashChainService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 审计发现业务服务（M3 审计管理核心；落地"外部审计对外回函三段漏斗"红线）。
 *
 * 隔离/留痕范式同 {@link AuditPlanService}：方法 @Transactional → 切面自动注入 visible_orgs，
 * RLS 裁剪 + WITH CHECK 校验；每次流转/推进调用 {@link HashChainService#append} 留痕（entity="AUDIT_FINDING:{id}"）。
 *
 * 两条独立轨：
 *  - 内部处置状态机：OPEN → ANALYZING → REMEDIATED → CLOSED（{@link #analyze}/{@link #remediate}/{@link #closeFinding}）。
 *
 * ===== 外部审计对外回函三段漏斗（M3 红线，本类的核心约束）=====
 * external_response_status 仅 audit_type=EXTERNAL 的发现可用，单向推进、不可跳级、不可逆向：
 *   SUBMITTED(已提交外部机构) → ACCEPTED(外方受理) → CLOSED(外方确认关闭)
 * 三个推进方法各自只推进一段：{@link #submitResponse}/{@link #acceptResponse}/{@link #confirmClose}；
 * 校验逻辑统一收敛于 {@link #advanceFunnel}：
 *   1) 非外审发现走漏斗 → 抛 {@link AuditFunnelException}；
 *   2) 目标段必须是当前段的相邻下一段（起点 null→SUBMITTED）；跳级/逆向/原地重复一律抛 {@link AuditFunnelException}；
 *   3) 唯 CLOSED 算外审闭环（{@link ExternalResponseStatus#isClosed()}）。
 *
 * 设计依据：需求文档 M3 审计管理（外审三段漏斗红线）、D2-5。
 */
@Service
public class AuditFindingService {

    private final AuditFindingRepository findingRepository;
    private final AuditPlanRepository planRepository;
    private final HashChainService hashChainService;
    private final RemediationOrderRepository remediationOrderRepository;

    public AuditFindingService(AuditFindingRepository findingRepository,
                               AuditPlanRepository planRepository,
                               HashChainService hashChainService,
                               RemediationOrderRepository remediationOrderRepository) {
        this.findingRepository = findingRepository;
        this.planRepository = planRepository;
        this.hashChainService = hashChainService;
        this.remediationOrderRepository = remediationOrderRepository;
    }

    /** 列出某审计计划下的全部发现（受 RLS 裁剪）。 */
    @Transactional(readOnly = true)
    public List<AuditFinding> listByPlan(Long auditPlanId) {
        return findingRepository.findByAuditPlanId(auditPlanId);
    }

    /** 按 id 取审计发现（不可见则视为不存在）。 */
    @Transactional(readOnly = true)
    public AuditFinding get(Long id) {
        return findingRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("审计发现不存在或不可见：id=" + id));
    }

    /**
     * 新建审计发现（OPEN 态，漏斗未进入）。
     *
     * @param orgId       归属组织（须在 visible_orgs 内）
     * @param auditPlanId 所属审计计划
     */
    @Transactional
    public AuditFinding createFinding(Long orgId, Long auditPlanId, String title,
                                      AuditSeverity severity, String actor) {
        AuditFinding f = new AuditFinding(orgId, auditPlanId, title, severity);
        AuditFinding saved = findingRepository.save(f);
        appendLog(saved, "AUDIT_FINDING_CREATE", actor,
                "新建审计发现 title=" + title + " severity=" + severity);
        return saved;
    }

    /** 调整严重度（不改变状态/漏斗）。 */
    @Transactional
    public AuditFinding setSeverity(Long id, AuditSeverity severity, String actor) {
        AuditFinding f = get(id);
        f.setSeverity(severity);
        AuditFinding saved = findingRepository.save(f);
        appendLog(saved, "AUDIT_FINDING_SEVERITY", actor, "调整严重度=" + severity);
        return saved;
    }

    // ---------- 内部处置状态机 ----------

    /** 开始分析：OPEN → ANALYZING。 */
    @Transactional
    public AuditFinding analyze(Long id, String actor) {
        AuditFinding f = get(id);
        transition(f, AuditFindingStatus.OPEN, AuditFindingStatus.ANALYZING);
        AuditFinding saved = findingRepository.save(f);
        appendLog(saved, "AUDIT_FINDING_ANALYZE", actor, "开始分析");
        return saved;
    }

    /**
     * 完成整改：ANALYZING → REMEDIATED。
     * 【整改验证闭环红线】须该发现已有 ≥1 条 VERIFIED 整改工单，否则禁止标记为已整改。
     */
    @Transactional
    public AuditFinding remediate(Long id, String actor) {
        AuditFinding f = get(id);
        if (!remediationOrderRepository.existsByFindingIdAndStatus(id, RemediationStatus.VERIFIED)) {
            throw new IllegalStateException(
                    "审计发现 id=" + id + " 无已验证的整改工单，不能标记为已整改（整改验证闭环）");
        }
        transition(f, AuditFindingStatus.ANALYZING, AuditFindingStatus.REMEDIATED);
        AuditFinding saved = findingRepository.save(f);
        appendLog(saved, "AUDIT_FINDING_REMEDIATE", actor, "完成整改（整改工单已验证闭环）");
        return saved;
    }

    /** 关闭发现：REMEDIATED → CLOSED（内部处置终态）。 */
    @Transactional
    public AuditFinding closeFinding(Long id, String actor) {
        AuditFinding f = get(id);
        transition(f, AuditFindingStatus.REMEDIATED, AuditFindingStatus.CLOSED);
        AuditFinding saved = findingRepository.save(f);
        appendLog(saved, "AUDIT_FINDING_CLOSE", actor, "关闭审计发现");
        return saved;
    }

    // ---------- 外部审计对外回函三段漏斗（红线） ----------

    /** 漏斗第一段：未进入(null) → SUBMITTED（提交外部机构）。仅外审。 */
    @Transactional
    public AuditFinding submitResponse(Long id, String actor) {
        return advanceFunnel(id, ExternalResponseStatus.SUBMITTED, actor);
    }

    /** 漏斗第二段：SUBMITTED → ACCEPTED（外方受理）。仅外审。 */
    @Transactional
    public AuditFinding acceptResponse(Long id, String actor) {
        return advanceFunnel(id, ExternalResponseStatus.ACCEPTED, actor);
    }

    /** 漏斗第三段（闭环）：ACCEPTED → CLOSED（外方确认关闭）。仅外审。 */
    @Transactional
    public AuditFinding confirmClose(Long id, String actor) {
        return advanceFunnel(id, ExternalResponseStatus.CLOSED, actor);
    }

    // ---------- 内部辅助 ----------

    /**
     * 漏斗单步推进的统一校验与执行（红线核心）。
     *
     * @param target 目标段（由三个推进方法各自固定传入，本方法仍按当前段动态校验合法性）
     * @throws AuditFunnelException 非外审走漏斗 / 跳级 / 逆向 / 原地重复
     */
    private AuditFinding advanceFunnel(Long id, ExternalResponseStatus target, String actor) {
        AuditFinding f = get(id);

        // 1) 仅外审可走对外回函漏斗
        AuditPlan plan = planRepository.findById(f.getAuditPlanId())
                .orElseThrow(() -> new IllegalStateException(
                        "审计发现 id=" + f.getId() + " 的所属计划不存在或不可见：planId=" + f.getAuditPlanId()));
        if (plan.getAuditType() != AuditType.EXTERNAL) {
            throw new AuditFunnelException(
                    "对外回函三段漏斗仅适用于外部审计(EXTERNAL)；审计发现 id=" + f.getId()
                            + " 所属计划类型为 " + plan.getAuditType() + "，禁止走漏斗。");
        }

        // 2) 必须是当前段的相邻下一段（起点 null→SUBMITTED）；跳级/逆向/原地重复一律拒绝
        ExternalResponseStatus current = f.getExternalResponseStatus();
        ExternalResponseStatus expectedNext = (current == null)
                ? ExternalResponseStatus.SUBMITTED
                : current.next();
        if (expectedNext == null) {
            throw new AuditFunnelException(
                    "审计发现 id=" + f.getId() + " 已处于漏斗终态 " + current
                            + "（外审已闭环），不可再推进。");
        }
        if (target != expectedNext) {
            throw new AuditFunnelException(
                    "外审回函漏斗非法流转：审计发现 id=" + f.getId()
                            + " 当前段=" + current
                            + "，仅允许单向推进到 " + expectedNext
                            + "（不允许跳级/逆向/重复），目标=" + target + " 被拒。");
        }

        f.setExternalResponseStatus(target);
        AuditFinding saved = findingRepository.save(f);
        appendLog(saved, "AUDIT_FINDING_FUNNEL", actor,
                "外审回函漏斗推进 " + current + " → " + target
                        + (target.isClosed() ? "（外审闭环）" : ""));
        return saved;
    }

    /** 校验并执行一次合法的内部处置流转：当前态须 == expectedFrom，否则抛异常。 */
    private void transition(AuditFinding f, AuditFindingStatus expectedFrom, AuditFindingStatus to) {
        if (f.getStatus() != expectedFrom) {
            throw new IllegalStateException(
                    "非法状态流转：审计发现 id=" + f.getId()
                            + " 当前状态=" + f.getStatus()
                            + "，仅允许从 " + expectedFrom + " 流转到 " + to);
        }
        f.setStatus(to);
    }

    /** 统一留痕入口：entity 统一格式 "AUDIT_FINDING:{id}"。 */
    private void appendLog(AuditFinding f, String action, String actor, String detail) {
        hashChainService.append(f.getOrgId(), action, actor, "AUDIT_FINDING:" + f.getId(), detail);
    }
}
