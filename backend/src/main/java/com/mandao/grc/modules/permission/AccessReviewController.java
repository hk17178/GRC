package com.mandao.grc.modules.permission;

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
 * UAR 权限审阅 REST 端点：/api/access-reviews（参照 AuditPlanController 风格）。
 *
 * 隔离/actor：可见范围由 X-User 头决定（切面注入 visible_orgs），actor 取 X-User。
 *
 * 审阅流程：create → start（快照有效授权）→ items/{id}/decide(KEEP|REVOKE) → complete；
 * 非法流转由 Service 抛 IllegalStateException。
 */
@RestController
@RequestMapping("/api/access-reviews")
public class AccessReviewController {

    private final AccessReviewService service;

    public AccessReviewController(AccessReviewService service) {
        this.service = service;
    }

    /** 取单个审阅。 */
    @GetMapping("/{id}")
    public AccessReview get(@PathVariable Long id) {
        return service.get(id);
    }

    /** 列出某审阅下的审阅项。 */
    @GetMapping("/{id}/items")
    public List<AccessReviewItem> items(@PathVariable Long id) {
        return service.listItems(id);
    }

    /** 新建权限审阅（OPEN 态）。 */
    @PostMapping
    @RequiresPermission("perm")
    public AccessReview create(@RequestBody CreateReviewRequest req,
                               @RequestHeader(value = "X-User", required = false) String user) {
        return service.createReview(req.orgId(), req.period(), req.reviewer(), actor(user));
    }

    /** 开始审阅：OPEN → IN_REVIEW（快照有效授权为审阅项）。 */
    @PostMapping("/{id}/start")
    @RequiresPermission("perm")
    public AccessReview start(@PathVariable Long id,
                              @RequestHeader(value = "X-User", required = false) String user) {
        return service.startReview(id, actor(user));
    }

    /** 对审阅项做决定（KEEP/REVOKE）。 */
    @PostMapping("/items/{itemId}/decide")
    @RequiresPermission("perm")
    public AccessReviewItem decide(@PathVariable Long itemId,
                                   @RequestBody DecideRequest req,
                                   @RequestHeader(value = "X-User", required = false) String user) {
        return service.decideItem(itemId, req.decision(), actor(user));
    }

    /** 完成审阅：IN_REVIEW → COMPLETED。 */
    @PostMapping("/{id}/complete")
    @RequiresPermission("perm")
    public AccessReview complete(@PathVariable Long id,
                                 @RequestHeader(value = "X-User", required = false) String user) {
        return service.completeReview(id, actor(user));
    }

    private String actor(String user) {
        return com.mandao.grc.common.auth.ActorResolver.resolve(user); // 七轮 7-4：登录态优先，消除 anonymous 归因
    }

    /** 新建审阅请求体。 */
    public record CreateReviewRequest(Long orgId, String period, String reviewer) {
    }

    /** 审阅决定请求体。 */
    public record DecideRequest(AccessReviewDecision decision) {
    }
}
