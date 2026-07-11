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
 * 自定义列表视图 REST 端点：/api/custom-views（B12 低代码 Phase2 / D1-8 H-05）。
 *
 * 隔离：可见范围由 X-User 头决定（切面注入 visible_orgs）；执行走编排器（白名单 + RLS 兜底），
 * 任意视图/筛选无路径越过 visibleOrgs。写门控复用 "org"（宿主 asset 属组织/资产域）。
 */
@RestController
@RequestMapping("/api/custom-views")
public class CustomViewController {

    private final CustomViewService service;

    public CustomViewController(CustomViewService service) {
        this.service = service;
    }

    /** 列出某对象类型的视图定义。 */
    @GetMapping
    @RequiresPermission("org")
    public List<CustomViewDef> list(@RequestParam String objectType) {
        return service.list(objectType);
    }

    /** 执行已登记视图，返回行（RLS 裁剪 + 行数封顶）。 */
    @GetMapping("/{id}/rows")
    @RequiresPermission("org")
    public List<Map<String, Object>> rows(@PathVariable Long id) {
        return service.execute(id);
    }

    /** 预览临时定义（不落库，供构建器即时查看）。 */
    @PostMapping("/preview")
    @RequiresPermission("org")
    public List<Map<String, Object>> preview(@RequestBody PreviewRequest req) {
        return service.preview(req.objectType(), req.definition());
    }

    @PostMapping
    @RequiresPermission("org")
    public CustomViewDef create(@RequestBody CreateRequest req,
                                @RequestHeader(value = "X-User", required = false) String user) {
        return service.create(req.orgId(), req.objectType(), req.name(), req.definition(), actor(user));
    }

    @PostMapping("/{id}/retire")
    @RequiresPermission("org")
    public CustomViewDef retire(@PathVariable Long id,
                                @RequestHeader(value = "X-User", required = false) String user) {
        return service.retire(id, actor(user));
    }

    private String actor(String user) {
        return com.mandao.grc.common.auth.ActorResolver.resolve(user);
    }

    /** definition 为声明式 JSON：{columns:[],filters:[{field,op,value}],sort:{field,dir}}。 */
    public record CreateRequest(Long orgId, String objectType, String name, String definition) {
    }

    public record PreviewRequest(String objectType, String definition) {
    }
}
