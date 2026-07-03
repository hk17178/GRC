package com.mandao.grc.modules.dashboard;

import com.mandao.grc.modules.assessment.RiskFinding;
import com.mandao.grc.modules.assessment.RiskFindingRepository;
import com.mandao.grc.modules.assessment.RiskFindingStatus;
import com.mandao.grc.modules.audit.management.AuditFindingRepository;
import com.mandao.grc.modules.audit.management.AuditFindingStatus;
import com.mandao.grc.modules.audit.management.RemediationOrderRepository;
import com.mandao.grc.modules.audit.management.RemediationStatus;
import com.mandao.grc.modules.kri.KriRepository;
import com.mandao.grc.modules.kri.KriStatus;
import com.mandao.grc.modules.permission.SodExceptionRepository;
import com.mandao.grc.modules.permission.SodExceptionStatus;
import com.mandao.grc.modules.policy.PolicyRepository;
import com.mandao.grc.modules.policy.PolicyStatus;
import com.mandao.grc.modules.regulatory.RegFilingRepository;
import com.mandao.grc.modules.regulatory.RegFilingStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 合规态势汇总服务（横切聚合，只读）。
 *
 * 跨模块只读聚合：注入各业务仓储，对【当前可见组织范围】内的数据计数，产出 {@link DashboardSummary}。
 * 隔离：方法 @Transactional 且位于 com.mandao.grc.modules 包 → 切面注入 visible_orgs，各 findAll 受 RLS 裁剪，
 * 故同一接口在不同主体下返回各自域内的态势（无需手写 org 过滤）。
 *
 * 说明：当前用 findAll + 内存计数（数据规模可控、且需按多维分类）；后续如量级增大可改投影/计数查询。
 *
 * 设计依据：需求文档「合规态势」仪表盘、D1-7、D2-5。
 */
@Service
public class DashboardService {

    private final RiskFindingRepository riskFindingRepository;
    private final KriRepository kriRepository;
    private final AuditFindingRepository auditFindingRepository;
    private final RemediationOrderRepository remediationOrderRepository;
    private final RegFilingRepository regFilingRepository;
    private final PolicyRepository policyRepository;
    private final SodExceptionRepository sodExceptionRepository;

    public DashboardService(RiskFindingRepository riskFindingRepository,
                            KriRepository kriRepository,
                            AuditFindingRepository auditFindingRepository,
                            RemediationOrderRepository remediationOrderRepository,
                            RegFilingRepository regFilingRepository,
                            PolicyRepository policyRepository,
                            SodExceptionRepository sodExceptionRepository) {
        this.riskFindingRepository = riskFindingRepository;
        this.kriRepository = kriRepository;
        this.auditFindingRepository = auditFindingRepository;
        this.remediationOrderRepository = remediationOrderRepository;
        this.regFilingRepository = regFilingRepository;
        this.policyRepository = policyRepository;
        this.sodExceptionRepository = sodExceptionRepository;
    }

    /** 汇总当前可见组织范围内的合规态势。 */
    @Transactional(readOnly = true)
    public DashboardSummary summary() {
        return new DashboardSummary(risk(), audit(), regulatory(), policy(), permission());
    }

    private DashboardSummary.Risk risk() {
        var findings = riskFindingRepository.findAll();
        long open = findings.stream().filter(f ->
                f.getStatus() == RiskFindingStatus.OPEN || f.getStatus() == RiskFindingStatus.IN_TREATMENT).count();
        long gated = findings.stream().filter(this::isGated).count();
        var kris = kriRepository.findAll();
        long kriWarning = kris.stream().filter(k -> k.getCurrentStatus() == KriStatus.WARNING).count();
        long kriCritical = kris.stream().filter(k -> k.getCurrentStatus() == KriStatus.CRITICAL).count();
        return new DashboardSummary.Risk(open, gated, kriWarning, kriCritical);
    }

    /** 被 CR-002 门控的发现：残余高/极高、无放行凭据、且尚未关闭。 */
    private boolean isGated(RiskFinding f) {
        boolean notClosed = f.getStatus() != RiskFindingStatus.DONE && f.getStatus() != RiskFindingStatus.VERIFIED;
        return notClosed && f.getRiskAcceptanceId() == null
                && f.getResidualLevel() != null && f.getResidualLevel().isHighResidual();
    }

    private DashboardSummary.Audit audit() {
        long open = auditFindingRepository.findAll().stream()
                .filter(f -> f.getStatus() != AuditFindingStatus.CLOSED).count();
        long pendingRemediation = remediationOrderRepository.findAll().stream()
                .filter(o -> o.getStatus() != RemediationStatus.VERIFIED).count();
        return new DashboardSummary.Audit(open, pendingRemediation);
    }

    private DashboardSummary.Regulatory regulatory() {
        var filings = regFilingRepository.findAll();
        long pending = filings.stream().filter(f ->
                f.getStatus() == RegFilingStatus.TO_DRAFT
                        || f.getStatus() == RegFilingStatus.DRAFTING
                        || f.getStatus() == RegFilingStatus.PENDING_REVIEW).count();
        long submitted = filings.stream().filter(f -> f.getStatus() == RegFilingStatus.SUBMITTED).count();
        return new DashboardSummary.Regulatory(pending, submitted);
    }

    private DashboardSummary.Policy policy() {
        var policies = policyRepository.findAll();
        long effective = policies.stream().filter(p -> p.getStatus() == PolicyStatus.EFFECTIVE).count();
        long inReview = policies.stream().filter(p -> p.getStatus() == PolicyStatus.REVIEW).count();
        long draft = policies.stream().filter(p -> p.getStatus() == PolicyStatus.DRAFT).count();
        return new DashboardSummary.Policy(effective, inReview, draft);
    }

    private DashboardSummary.Permission permission() {
        long pendingSod = sodExceptionRepository.findAll().stream()
                .filter(e -> e.getStatus() == SodExceptionStatus.PENDING).count();
        return new DashboardSummary.Permission(pendingSod);
    }

    /**
     * 风险等级分布（驾驶舱真值组件）：按发现的"残余优先、无残余取固有"等级计数，
     * 键为五级枚举名 + UNSET（未定级）。可见范围由 RLS 裁剪。
     */
    @Transactional(readOnly = true)
    public java.util.Map<String, Long> riskLevelDist() {
        java.util.Map<String, Long> out = new java.util.LinkedHashMap<>();
        for (var f : riskFindingRepository.findAll()) {
            var lv = f.getResidualLevel() != null ? f.getResidualLevel() : f.getInherentLevel();
            String key = lv == null ? "UNSET" : lv.name();
            out.merge(key, 1L, Long::sum);
        }
        return out;
    }
}
