package com.mandao.grc.modules.audit.management;

import com.mandao.grc.modules.rbac.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 整改工单 REST 端点：/api/remediation-orders。
 *
 * 隔离：可见范围由 X-User 头决定；actor 取 X-User，缺省 anonymous。
 */
@RestController
@RequestMapping("/api/remediation-orders")
public class RemediationController {

    private final RemediationService service;

    public RemediationController(RemediationService service) {
        this.service = service;
    }

    /** 列出某审计发现的整改工单。 */
    @GetMapping
    public List<RemediationOrder> listByFinding(@RequestParam(required = false) Long findingId,
                                                @RequestParam(required = false) AuditType type) {
        // 二选一：按发现查或按审计类型跨发现汇总查
        if (findingId != null) {
            return service.listByFinding(findingId);
        }
        if (type != null) {
            return service.listByType(type);
        }
        throw new IllegalArgumentException("请提供 findingId 或 type 参数");
    }

    /** 取单个整改工单。 */
    @GetMapping("/{id}")
    public RemediationOrder get(@PathVariable Long id) {
        return service.get(id);
    }

    /** 派单。 */
    @PostMapping
    @RequiresPermission("extaudit")
    public RemediationOrder create(@RequestBody CreateRequest req,
                                   @RequestHeader(value = "X-User", required = false) String user) {
        return service.create(req.findingId(), req.assignee(), req.dueDate(), req.measure(), actor(user));
    }

    /** 开始整改：PENDING → IN_PROGRESS。 */
    @PostMapping("/{id}/start")
    @RequiresPermission("extaudit")
    public RemediationOrder start(@PathVariable Long id,
                                  @RequestHeader(value = "X-User", required = false) String user) {
        return service.start(id, actor(user));
    }

    /** 提交整改：IN_PROGRESS → SUBMITTED。 */
    @PostMapping("/{id}/submit")
    @RequiresPermission("extaudit")
    public RemediationOrder submit(@PathVariable Long id,
                                   @RequestBody(required = false) SubmitRequest req,
                                   @RequestHeader(value = "X-User", required = false) String user) {
        return service.submit(id, req == null ? null : req.evidence(), actor(user));
    }

    /** 验证通过：SUBMITTED → VERIFIED。 */
    @PostMapping("/{id}/verify")
    @RequiresPermission("extaudit")
    public RemediationOrder verify(@PathVariable Long id,
                                   @RequestHeader(value = "X-User", required = false) String user) {
        return service.verify(id, actor(user));
    }

    /** 验证不通过：SUBMITTED → IN_PROGRESS（退回）。 */
    @PostMapping("/{id}/reject")
    @RequiresPermission("extaudit")
    public RemediationOrder reject(@PathVariable Long id,
                                   @RequestBody(required = false) RejectRequest req,
                                   @RequestHeader(value = "X-User", required = false) String user) {
        return service.reject(id, req == null ? null : req.reason(), actor(user));
    }

    private String actor(String user) {
        return (user == null || user.isBlank()) ? "anonymous" : user;
    }

    /** 派单请求体。 */
    public record CreateRequest(Long findingId, String assignee, LocalDate dueDate, String measure) {
    }

    /** 提交请求体。 */
    public record SubmitRequest(String evidence) {
    }

    /** 退回请求体。 */
    public record RejectRequest(String reason) {
    }
}
