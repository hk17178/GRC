package com.mandao.grc.modules.dashboard;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 合规态势 REST 端点：/api/dashboard。
 *
 * 隔离：可见范围由 X-User 头决定（切面注入 visible_orgs）；汇总仅覆盖可见组织。
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    /** 当前主体可见范围内的合规态势汇总。 */
    @GetMapping("/summary")
    public DashboardSummary summary() {
        return service.summary();
    }
}
