package com.mandao.grc.modules.assessment;

import com.mandao.grc.modules.workflow.ApprovalDecision;
import com.mandao.grc.modules.rbac.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 风险发现 REST 端点：/api/risk-findings（参照 PolicyController 风格）。
 *
 * 隔离/actor 同 {@link AssessmentController}：可见范围由 X-User 头决定，actor 取 X-User。
 *
 * 关闭门控（CR-002 红线）：close 端点在残余高/极高且无有效风险接受时，由 Service 抛
 * {@link RiskCloseGateException} 阻断，应阻止关闭。
 */
@RestController
@RequestMapping("/api/risk-findings")
public class RiskFindingController {

    private final RiskFindingService service;

    public RiskFindingController(RiskFindingService service) {
        this.service = service;
    }

    /** 列出某评估下的风险发现。 */
    @GetMapping
    public List<RiskFinding> listByAssessment(@RequestParam Long assessmentId) {
        return service.listByAssessment(assessmentId);
    }

    /** 取单个风险发现。 */
    @GetMapping("/{id}")
    public RiskFinding get(@PathVariable Long id) {
        return service.get(id);
    }

    /** 新建风险发现（OPEN 态）。 */
    @PostMapping
    @RequiresPermission("risk")
    public RiskFinding create(@RequestBody CreateFindingRequest req,
                              @RequestHeader(value = "X-User", required = false) String user) {
        return service.createFinding(req.orgId(), req.assessmentId(), req.title(),
                req.inherentLevel(), actor(user));
    }

    /** 录入处置方案：OPEN → IN_TREATMENT。 */
    @PostMapping("/{id}/treatment")
    @RequiresPermission("risk")
    public RiskFinding setTreatment(@PathVariable Long id,
                                    @RequestBody TreatmentRequest req,
                                    @RequestHeader(value = "X-User", required = false) String user) {
        return service.setTreatment(id, req.treatmentPlan(), actor(user));
    }

    /** 评估残余风险等级（不改变状态）。 */
    @PostMapping("/{id}/residual")
    @RequiresPermission("risk")
    public RiskFinding setResidual(@PathVariable Long id,
                                   @RequestBody ResidualRequest req,
                                   @RequestHeader(value = "X-User", required = false) String user) {
        return service.setResidual(id, req.residualLevel(), actor(user));
    }

    /**
     * 申请风险接受（A2 审批化）：登记 PENDING 接受并启动审批流，暂不放行（门控仍生效）。
     * 申请人取 X-User。
     */
    @PostMapping("/{id}/request-acceptance")
    @RequiresPermission("risk.requestAccept")
    public RiskAcceptance requestAcceptance(@PathVariable Long id,
                                            @RequestBody(required = false) RequestAcceptanceRequest req,
                                            @RequestHeader(value = "X-User", required = false) String user) {
        return service.requestAcceptance(id, actor(user), req == null ? null : req.reason(), actor(user));
    }

    /** 审批通过风险接受：回填放行凭据 → CR-002 门控解除。审批人取 X-User。 */
    @PostMapping("/{id}/accept-approve")
    @RequiresPermission("risk.approveAccept")
    public RiskAcceptance acceptApprove(@PathVariable Long id,
                                        @RequestBody(required = false) DecideRequest req,
                                        @RequestHeader(value = "X-User", required = false) String user) {
        return service.decideAcceptance(id, ApprovalDecision.APPROVED, actor(user), req == null ? null : req.comment());
    }

    /** 审批驳回风险接受：不放行（门控保持）。审批人取 X-User。 */
    @PostMapping("/{id}/accept-reject")
    @RequiresPermission("risk.approveAccept")
    public RiskAcceptance acceptReject(@PathVariable Long id,
                                       @RequestBody(required = false) DecideRequest req,
                                       @RequestHeader(value = "X-User", required = false) String user) {
        return service.decideAcceptance(id, ApprovalDecision.REJECTED, actor(user), req == null ? null : req.comment());
    }

    /**
     * 关闭：OPEN/IN_TREATMENT → DONE（verify=false）或 DONE → VERIFIED（verify=true）。
     * 受 CR-002 残余风险关闭门控约束。
     */
    @PostMapping("/{id}/close")
    @RequiresPermission("risk.closeFinding")
    public RiskFinding close(@PathVariable Long id,
                             @RequestParam(defaultValue = "false") boolean verify,
                             @RequestHeader(value = "X-User", required = false) String user) {
        return service.close(id, verify, actor(user));
    }

    private String actor(String user) {
        return (user == null || user.isBlank()) ? "anonymous" : user;
    }

    /** 新建风险发现请求体。 */
    public record CreateFindingRequest(Long orgId, Long assessmentId, String title, RiskLevel inherentLevel) {
    }

    /** 处置方案请求体。 */
    public record TreatmentRequest(String treatmentPlan) {
    }

    /** 残余等级请求体。 */
    public record ResidualRequest(RiskLevel residualLevel) {
    }

    /** 申请风险接受请求体（理由可选）。 */
    public record RequestAcceptanceRequest(String reason) {
    }

    /** 审批处置请求体（意见/驳回原因可选）。 */
    public record DecideRequest(String comment) {
    }
}
