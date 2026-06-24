package com.mandao.grc.modules.workbench;

import org.springframework.web.bind.annotation.GetMapping;
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

    /** 通知中心（调度内核派发的提醒，新→旧）。 */
    @GetMapping("/notifications")
    public List<NotificationView> notifications(@RequestParam(required = false) Integer limit) {
        return service.notifications(limit);
    }
}
