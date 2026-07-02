package com.mandao.grc.modules.workbench;

import com.mandao.grc.common.auth.CurrentUserContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
    public List<TodoItem> todos() {
        return service.todos();
    }

    /** 我的审批待办（按登录人角色匹配的待处理审批任务）。 */
    @GetMapping("/my-approvals")
    public List<MyApprovalItem> myApprovals() {
        return service.myApprovals();
    }

    /** 通知中心（调度内核派发的提醒，新→旧；同对象同事件合并降噪）。 */
    @GetMapping("/notifications")
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
    public List<WorkbenchService.DigestRow> digest(@RequestParam(required = false) Integer days) {
        return service.digest(days);
    }
}
