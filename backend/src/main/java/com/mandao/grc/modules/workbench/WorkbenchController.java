package com.mandao.grc.modules.workbench;

import com.mandao.grc.common.auth.CurrentUserContext;
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
 * 工作台 REST 端点：/api/workbench（我的待办 + 通知中心）。
 *
 * 隔离：可见范围由 X-User 头决定（切面注入 visible_orgs）；待办/通知均仅覆盖可见组织。
 */
@RestController
@RequestMapping("/api/workbench")
public class WorkbenchController {

    private final WorkbenchService service;

    public WorkbenchController(WorkbenchService service) {
        this.service = service;
    }

    /** 我的待办（可见范围内待处理工作的统一聚合）。 */
    @GetMapping("/todos")
    @RequiresPermission("todo")
    public List<TodoItem> todos() {
        return service.todos();
    }

    /** 我的审批待办（按登录人角色匹配的待处理审批任务）。 */
    @GetMapping("/my-approvals")
    @RequiresPermission("todo")
    public List<MyApprovalItem> myApprovals() {
        return service.myApprovals();
    }

    /** M8-5 转办：把审批任务改派给指定人（职责分离——不得转办给发起人）。 */
    @PostMapping("/approvals/{taskId}/reassign")
    public void reassignApproval(@PathVariable String taskId, @RequestBody TaskTargetRequest req) {
        service.reassignApproval(taskId, req.user());
    }

    /** M8-5 加签：为审批任务追加一名候选审批人（或签；职责分离——不得对发起人加签）。 */
    @PostMapping("/approvals/{taskId}/add-signer")
    public void addApprovalSigner(@PathVariable String taskId, @RequestBody TaskTargetRequest req) {
        service.addApprovalSigner(taskId, req.user());
    }

    /** 加签/转办目标用户名。 */
    public record TaskTargetRequest(String user) {
    }

    /** M10-11 批量委派：多个审批任务一次性转办给同一人。 */
    @PostMapping("/approvals/reassign-batch")
    public void reassignApprovalsBatch(@RequestBody BatchReassignRequest req) {
        service.reassignApprovals(req.taskIds(), req.user());
    }

    /** M10-11 批量回执：一次性确认多条提醒已读，返回实际回执条数。 */
    @PostMapping("/notifications/ack-batch")
    public java.util.Map<String, Integer> ackNotificationsBatch(@RequestBody AckBatchRequest req,
                                                                @RequestHeader(value = "X-User", required = false) String user) {
        String actor = CurrentUserContext.get() != null ? CurrentUserContext.get()
                : (user == null || user.isBlank() ? "anonymous" : user);
        return java.util.Map.of("acked", service.ackNotifications(req.ids(), actor));
    }

    public record BatchReassignRequest(java.util.List<String> taskIds, String user) {
    }

    public record AckBatchRequest(java.util.List<Long> ids) {
    }

    /** 通知中心（调度内核派发的提醒，新→旧；同对象同事件合并降噪）。 */
    @GetMapping("/notifications")
    @RequiresPermission("notify")
    public List<NotificationView> notifications(@RequestParam(required = false) Integer limit) {
        return service.notifications(limit);
    }

    /** 通知回执：确认收到某条提醒（回执人=登录人，X-User 兜底）。 */
    @PostMapping("/notifications/{id}/ack")
    public void ackNotification(@PathVariable Long id,
                                @RequestHeader(value = "X-User", required = false) String user) {
        String actor = CurrentUserContext.get() != null ? CurrentUserContext.get()
                : (user == null || user.isBlank() ? "anonymous" : user);
        service.ackNotification(id, actor);
    }

    /** 定期简报：近 N 天提醒按事件类型聚合（总数/未回执数），默认 7 天。 */
    @GetMapping("/digest")
    @RequiresPermission("notify")
    public List<WorkbenchService.DigestRow> digest(@RequestParam(required = false) Integer days) {
        return service.digest(days);
    }

    // ===== B28：通知订阅偏好（登录人维度）=====

    /** 读当前登录人静音的通知分类。 */
    @GetMapping("/notify-preference")
    @RequiresPermission("notify")
    public List<String> getNotifyPreference() {
        return service.getMutedCategories();
    }

    /** 保存当前登录人静音的通知分类（法定时限红线分类会被强制剔除）。 */
    @PostMapping("/notify-preference")
    public void setNotifyPreference(@RequestBody NotifyPreferenceRequest req) {
        service.setMutedCategories(req.mutedCategories());
    }

    /** 通知偏好请求体（mutedCategories：静音分类键数组）。 */
    public record NotifyPreferenceRequest(List<String> mutedCategories) {
    }
}
