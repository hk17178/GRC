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
import java.util.Map;

/**
 * 自定义报表 REST 端点：/api/custom-reports（B12 低代码 Phase3 / D1-8 §六）。
 *
 * 隔离：可见范围由 X-User 头决定（切面注入 visible_orgs）；执行/导出走聚合编排器（白名单+聚合枚举+RLS 兜底），
 * 报表数据集无路径越过 visibleOrgs。导出入 operation_log 留痕。写门控复用 "org"。
 */
@RestController
@RequestMapping("/api/custom-reports")
public class CustomReportController {

    private final CustomReportService service;

    public CustomReportController(CustomReportService service) {
        this.service = service;
    }

    /** 列出某对象类型的报表定义。 */
    @GetMapping
    public List<CustomReportDef> list(@RequestParam String objectType) {
        return service.list(objectType);
    }

    /** 执行已登记报表，返回聚合行（RLS 裁剪 + 行数封顶）。 */
    @GetMapping("/{id}/rows")
    public List<Map<String, Object>> rows(@PathVariable Long id) {
        return service.execute(id);
    }

    /** 预览临时定义（不落库，供构建器即时查看）。 */
    @PostMapping("/preview")
    public List<Map<String, Object>> preview(@RequestBody PreviewRequest req) {
        return service.preview(req.objectType(), req.definition());
    }

    /** 导出报表：执行 + 导出动作入 operation_log，返回行供前端生成 CSV。 */
    @PostMapping("/{id}/export")
    public List<Map<String, Object>> export(@PathVariable Long id,
                                            @RequestHeader(value = "X-User", required = false) String user) {
        return service.export(id, actor(user));
    }

    @PostMapping
    @RequiresPermission("org")
    public CustomReportDef create(@RequestBody CreateRequest req,
                                  @RequestHeader(value = "X-User", required = false) String user) {
        return service.create(req.orgId(), req.objectType(), req.name(), req.definition(), actor(user));
    }

    @PostMapping("/{id}/retire")
    @RequiresPermission("org")
    public CustomReportDef retire(@PathVariable Long id,
                                  @RequestHeader(value = "X-User", required = false) String user) {
        return service.retire(id, actor(user));
    }

    private String actor(String user) {
        return com.mandao.grc.common.auth.ActorResolver.resolve(user);
    }

    /** definition 为声明式 JSON：{groupBy:[],measures:[{agg,field}],filters:[{field,op,value}]}。 */
    public record CreateRequest(Long orgId, String objectType, String name, String definition) {
    }

    public record PreviewRequest(String objectType, String definition) {
    }
}
