package com.mandao.grc.modules.policy;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 制度体系 REST 端点：/api/policies（参照 AssessmentController 风格）。
 *
 * 隔离：与评估端点一致，可见范围由请求经 {@link com.mandao.grc.common.isolation.IsolationFilter}
 * 解析的 X-User 头决定，本控制器不再处理 org 过滤。
 *
 * actor（操作人）暂从请求头 X-User 取（与隔离主体同源）；缺省占位 "anonymous"。
 * 正式鉴权接入后由安全上下文提供。
 */
@RestController
@RequestMapping("/api/policies")
public class PolicyController {

    private final PolicyService service;

    public PolicyController(PolicyService service) {
        this.service = service;
    }

    /** 列出当前主体可见组织范围内的制度。 */
    @GetMapping
    public List<Policy> list() {
        return service.list();
    }

    /** 取单个制度（不可见则视为不存在）。 */
    @GetMapping("/{id}")
    public Policy get(@PathVariable Long id) {
        return service.get(id);
    }

    /** 新建草稿制度。 */
    @PostMapping
    public Policy create(@RequestBody CreatePolicyRequest req,
                         @RequestHeader(value = "X-User", required = false) String user) {
        return service.create(req.orgId(), req.code(), req.title(), req.content(), actor(user));
    }

    /** 提交评审：DRAFT → REVIEW。 */
    @PostMapping("/{id}/submit")
    public Policy submit(@PathVariable Long id,
                         @RequestHeader(value = "X-User", required = false) String user) {
        return service.submitForApproval(id, actor(user));
    }

    /** 审批通过：REVIEW → EFFECTIVE。 */
    @PostMapping("/{id}/approve")
    public Policy approve(@PathVariable Long id,
                          @RequestHeader(value = "X-User", required = false) String user) {
        return service.approve(id, actor(user));
    }

    /** 审批驳回：REVIEW → DRAFT。 */
    @PostMapping("/{id}/reject")
    public Policy reject(@PathVariable Long id,
                         @RequestBody(required = false) RejectRequest req,
                         @RequestHeader(value = "X-User", required = false) String user) {
        return service.reject(id, actor(user), req == null ? null : req.reason());
    }

    /** 废止：EFFECTIVE → DEPRECATED。 */
    @PostMapping("/{id}/archive")
    public Policy archive(@PathVariable Long id,
                          @RequestHeader(value = "X-User", required = false) String user) {
        return service.archive(id, actor(user));
    }

    /** 签署确认（仅 EFFECTIVE 可签）。 */
    @PostMapping("/{id}/signoff")
    public PolicySignoff signoff(@PathVariable Long id,
                                 @RequestHeader(value = "X-User", required = false) String user) {
        return service.signoff(id, actor(user));
    }

    /** actor 占位策略：取 X-User，缺省 anonymous。 */
    private String actor(String user) {
        return (user == null || user.isBlank()) ? "anonymous" : user;
    }

    /** 新建制度请求体。 */
    public record CreatePolicyRequest(Long orgId, String code, String title, String content) {
    }

    /** 驳回请求体（原因可选）。 */
    public record RejectRequest(String reason) {
    }
}
