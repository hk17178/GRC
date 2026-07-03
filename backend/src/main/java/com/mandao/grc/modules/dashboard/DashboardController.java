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
    private final OrgSummaryService orgSummaryService;

    public DashboardController(DashboardService service, OrgSummaryService orgSummaryService) {
        this.service = service;
        this.orgSummaryService = orgSummaryService;
    }

    /** 当前主体可见范围内的合规态势汇总。 */
    @GetMapping("/summary")
    public DashboardSummary summary() {
        return service.summary();
    }

    /** 按组织聚合：热力矩阵六域真值计数 + 整改完成率（驾驶舱下钻用）。 */
    @GetMapping("/org-summary")
    public java.util.List<OrgSummaryService.OrgRow> orgSummary() {
        return orgSummaryService.orgSummary();
    }

    /** 风险等级分布（真值组件）：残余优先、无残余取固有。 */
    @GetMapping("/risk-levels")
    public java.util.Map<String, Long> riskLevels() {
        return service.riskLevelDist();
    }
}
