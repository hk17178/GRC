package com.mandao.grc.modules.regulatory;

import com.mandao.grc.modules.rbac.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 年度合规计划 REST 端点：/api/compliance-plans。
 *
 * 隔离/actor：可见范围由 X-User 头决定；actor 取 X-User，缺省 anonymous。
 */
@RestController
@RequestMapping("/api/compliance-plans")
public class CompliancePlanController {

    private final CompliancePlanService service;

    public CompliancePlanController(CompliancePlanService service) {
        this.service = service;
    }

    @GetMapping
    public List<CompliancePlan> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public CompliancePlan get(@PathVariable Long id) {
        return service.get(id);
    }

    @GetMapping("/{id}/items")
    public List<CompliancePlanItem> items(@PathVariable Long id) {
        return service.listItems(id);
    }

    @PostMapping
    @RequiresPermission("regaffairs")
    public CompliancePlan create(@RequestBody CreatePlanRequest req,
                                 @RequestHeader(value = "X-User", required = false) String user) {
        return service.create(req.orgId(), req.year(), req.title(), req.owner(), actor(user));
    }

    @PostMapping("/{id}/items")
    @RequiresPermission("regaffairs")
    public CompliancePlanItem addItem(@PathVariable Long id,
                                      @RequestBody AddItemRequest req,
                                      @RequestHeader(value = "X-User", required = false) String user) {
        return service.addItem(id, req.matter(), req.ownerDept(), req.dueDate(), actor(user));
    }

    @PostMapping("/{id}/activate")
    @RequiresPermission("regaffairs")
    public CompliancePlan activate(@PathVariable Long id,
                                   @RequestHeader(value = "X-User", required = false) String user) {
        return service.activate(id, actor(user));
    }

    @PostMapping("/{id}/close")
    @RequiresPermission("regaffairs")
    public CompliancePlan close(@PathVariable Long id,
                                @RequestHeader(value = "X-User", required = false) String user) {
        return service.close(id, actor(user));
    }

    @PostMapping("/items/{itemId}/status")
    @RequiresPermission("regaffairs")
    public CompliancePlanItem updateItemStatus(@PathVariable Long itemId,
                                               @RequestBody ItemStatusRequest req,
                                               @RequestHeader(value = "X-User", required = false) String user) {
        return service.updateItemStatus(itemId, req.status(), actor(user));
    }

    private String actor(String user) {
        return com.mandao.grc.common.auth.ActorResolver.resolve(user); // 七轮 7-4：登录态优先，消除 anonymous 归因
    }

    /** 新建计划请求体。 */
    public record CreatePlanRequest(Long orgId, Integer year, String title, String owner) {
    }

    /** 追加计划项请求体。 */
    public record AddItemRequest(String matter, String ownerDept, LocalDate dueDate) {
    }

    /** 更新计划项状态请求体。 */
    public record ItemStatusRequest(CompliancePlanItemStatus status) {
    }
}
