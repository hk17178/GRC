package com.mandao.grc.modules.custom;

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
 * 自定义看板 REST 端点：/api/dashboards（B12 低代码 Phase5 / D1-8 §五）。
 *
 * 隔离：可见范围由 X-User 头决定（切面注入 visible_orgs）；渲染逐组件走标准聚合接口（KPI/报表，均经 RLS），
 * 看板无路径越过 visibleOrgs。写门控复用 "org"。
 */
@RestController
@RequestMapping("/api/dashboards")
public class CustomDashboardController {

    private final CustomDashboardService service;

    public CustomDashboardController(CustomDashboardService service) {
        this.service = service;
    }

    /** 列出看板定义。 */
    @GetMapping
    public List<DashboardDef> list() {
        return service.list();
    }

    /** 渲染已登记看板（逐组件解析数据）。 */
    @GetMapping("/{id}/render")
    public CustomDashboardService.DashboardRender render(@PathVariable Long id) {
        return service.render(id);
    }

    /** 预览临时布局（不落库）。 */
    @PostMapping("/preview")
    public CustomDashboardService.DashboardRender preview(@RequestBody PreviewRequest req) {
        return service.preview(req.name(), req.layout());
    }

    @PostMapping
    @RequiresPermission("org")
    public DashboardDef create(@RequestBody CreateRequest req,
                               @RequestHeader(value = "X-User", required = false) String user) {
        return service.create(req.orgId(), req.name(), req.layout(), actor(user));
    }

    @PostMapping("/{id}/retire")
    @RequiresPermission("org")
    public DashboardDef retire(@PathVariable Long id,
                               @RequestHeader(value = "X-User", required = false) String user) {
        return service.retire(id, actor(user));
    }

    private String actor(String user) {
        return com.mandao.grc.common.auth.ActorResolver.resolve(user);
    }

    /** layout 为声明式 JSON：{widgets:[{type:'KPI'|'REPORT',refId,title}]}。 */
    public record CreateRequest(Long orgId, String name, String layout) {
    }

    public record PreviewRequest(String name, String layout) {
    }
}
