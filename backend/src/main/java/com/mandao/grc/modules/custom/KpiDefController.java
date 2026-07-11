package com.mandao.grc.modules.custom;

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
 * 自定义 KPI REST 端点：/api/custom-kpis（B12 低代码 Phase4 / D1-8 §七）。
 *
 * 隔离：可见范围由 X-User 头决定（切面注入 visible_orgs）；求值走公式引擎（白名单聚合 + 安全表达式 + RLS 兜底），
 * KPI 数据集无路径越过 visibleOrgs。写门控复用 "org"。
 */
@RestController
@RequestMapping("/api/custom-kpis")
public class KpiDefController {

    private final KpiDefService service;

    public KpiDefController(KpiDefService service) {
        this.service = service;
    }

    /** 列出某对象类型的 KPI 定义。 */
    @GetMapping
    @RequiresPermission("org")
    public List<KpiDef> list(@RequestParam String objectType) {
        return service.list(objectType);
    }

    /** 求值已登记 KPI，返回 {id,name,value,unit}（value 为 null 表示不可计算）。 */
    @GetMapping("/{id}/value")
    @RequiresPermission("org")
    public KpiDefService.KpiResult value(@PathVariable Long id) {
        return service.evaluate(id);
    }

    /** 预览临时公式（不落库，供构建器即时查看）。 */
    @PostMapping("/preview")
    @RequiresPermission("org")
    public KpiDefService.KpiResult preview(@RequestBody PreviewRequest req) {
        return service.preview(req.objectType(), req.name(), req.formula(), req.unit());
    }

    @PostMapping
    @RequiresPermission("org")
    public KpiDef create(@RequestBody CreateRequest req,
                         @RequestHeader(value = "X-User", required = false) String user) {
        return service.create(req.orgId(), req.objectType(), req.name(), req.formula(), req.unit(), actor(user));
    }

    @PostMapping("/{id}/retire")
    @RequiresPermission("org")
    public KpiDef retire(@PathVariable Long id,
                         @RequestHeader(value = "X-User", required = false) String user) {
        return service.retire(id, actor(user));
    }

    private String actor(String user) {
        return com.mandao.grc.common.auth.ActorResolver.resolve(user);
    }

    /** formula 为声明式 JSON DSL：{terms:{a:{agg,field,filters}},expr,decimals}。 */
    public record CreateRequest(Long orgId, String objectType, String name, String formula, String unit) {
    }

    public record PreviewRequest(String objectType, String name, String formula, String unit) {
    }
}
