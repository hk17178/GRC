package com.mandao.grc.modules.assessment;

import com.mandao.grc.modules.rbac.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 风险评估 REST 端点：/api/assessments（参照 PolicyController 风格）。
 *
 * 隔离：可见范围由请求经 {@link com.mandao.grc.common.isolation.IsolationFilter} 解析的 X-User 头决定，
 * 本控制器不处理 org 过滤。actor（操作人）取请求头 X-User；缺省占位 "anonymous"。
 */
@RestController
@RequestMapping("/api/assessments")
public class AssessmentController {

    private final AssessmentService service;

    public AssessmentController(AssessmentService service) {
        this.service = service;
    }

    /** 列出当前主体可见组织范围内的评估。 */
    @GetMapping
    public List<Assessment> list() {
        return service.list();
    }

    /** 取单个评估（不可见则视为不存在）。 */
    @GetMapping("/{id}")
    public Assessment get(@PathVariable Long id) {
        return service.get(id);
    }

    /** 新建草稿评估。 */
    @PostMapping
    @RequiresPermission("risk.create")
    public Assessment create(@RequestBody CreateAssessmentRequest req,
                             @RequestHeader(value = "X-User", required = false) String user) {
        return service.create(req.orgId(), req.title(), req.assessor(), req.period(), req.templateId(), actor(user));
    }

    /** 开始评估：DRAFT → IN_PROGRESS。 */
    @PostMapping("/{id}/start")
    @RequiresPermission("risk")
    public Assessment start(@PathVariable Long id,
                            @RequestHeader(value = "X-User", required = false) String user) {
        return service.start(id, actor(user));
    }

    /** 提交复核：IN_PROGRESS → PENDING_REVIEW。 */
    @PostMapping("/{id}/submit")
    @RequiresPermission("risk")
    public Assessment submit(@PathVariable Long id,
                             @RequestHeader(value = "X-User", required = false) String user) {
        return service.submitForReview(id, actor(user));
    }

    /** 复核驳回：PENDING_REVIEW → IN_PROGRESS。 */
    @PostMapping("/{id}/reject")
    @RequiresPermission("risk")
    public Assessment reject(@PathVariable Long id,
                            @RequestBody(required = false) RejectRequest req,
                            @RequestHeader(value = "X-User", required = false) String user) {
        return service.reject(id, actor(user), req == null ? null : req.reason());
    }

    /** 完成评估：PENDING_REVIEW → COMPLETED（残余高/极高需管理层签批，否则 409）。 */
    @PostMapping("/{id}/complete")
    @RequiresPermission("risk")
    public Assessment complete(@PathVariable Long id,
                               @RequestHeader(value = "X-User", required = false) String user) {
        return service.complete(id, actor(user));
    }

    /** 管理层签批：记录意见 + 是否接受残余风险（放行高/极高残余评估的完成门控）。 */
    @PostMapping("/{id}/signoff")
    @RequiresPermission("risk")
    public Assessment signoff(@PathVariable Long id,
                             @RequestBody SignoffRequest req,
                             @RequestHeader(value = "X-User", required = false) String user) {
        return service.signOff(id, actor(user), req == null ? null : req.opinion(),
                req != null && req.accepted());
    }

    /**
     * actor 归属策略：优先取登录态（JWT Cookie 解析出的当前用户），其次兼容旧 X-User 头，再缺省 anonymous。
     *
     * R1 认证切到 httpOnly Cookie 后前端不再发 X-User，签批/留痕等审计归属须取真实登录人。
     */
    private String actor(String user) {
        String current = com.mandao.grc.common.auth.CurrentUserContext.get();
        if (current != null && !current.isBlank()) {
            return current;
        }
        return (user == null || user.isBlank()) ? "anonymous" : user;
    }

    /** 新建评估请求体（templateId 可空：关联来源模板则启用表单引擎填写）。 */
    public record CreateAssessmentRequest(Long orgId, String title, String assessor, String period, Long templateId) {
    }

    /** 驳回请求体（原因可选）。 */
    public record RejectRequest(String reason) {
    }

    /** 管理层签批请求体。 */
    public record SignoffRequest(String opinion, boolean accepted) {
    }
}
