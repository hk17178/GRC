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
 * 自定义字段 REST 端点：/api/custom-fields（B12 低代码 Phase1）。
 *
 * 隔离/actor：可见范围由 X-User 头决定（切面注入 visible_orgs），actor 登录态优先。
 * 写门控复用 "org"（宿主 asset 属组织/资产管理域）。
 */
@RestController
@RequestMapping("/api/custom-fields")
public class CustomFieldController {

    private final CustomFieldService service;

    public CustomFieldController(CustomFieldService service) {
        this.service = service;
    }

    /** 列出某对象类型的字段定义（含停用，供配置页）。 */
    @GetMapping
    @RequiresPermission("org")
    public List<CustomFieldDef> list(@RequestParam String objectType) {
        return service.list(objectType);
    }

    /** 列出某对象类型的启用字段（供宿主表单动态渲染）。 */
    @GetMapping("/active")
    @RequiresPermission("org")
    public List<CustomFieldDef> listActive(@RequestParam String objectType) {
        return service.listActive(objectType);
    }

    @PostMapping
    @RequiresPermission("org")
    public CustomFieldDef create(@RequestBody CreateRequest req,
                                 @RequestHeader(value = "X-User", required = false) String user) {
        return service.create(req.orgId(), req.objectType(), req.fieldKey(), req.label(),
                CustomFieldDef.DataType.valueOf(req.dataType()), req.options(),
                req.required(), req.sensitive(), req.aggregatable(),
                req.seq() == null ? 0 : req.seq(), actor(user));
    }

    @PostMapping("/{id}/retire")
    @RequiresPermission("org")
    public CustomFieldDef retire(@PathVariable Long id,
                                 @RequestHeader(value = "X-User", required = false) String user) {
        return service.retire(id, actor(user));
    }

    private String actor(String user) {
        return com.mandao.grc.common.auth.ActorResolver.resolve(user);
    }

    /** 登记字段请求体（dataType ∈ TEXT/NUMBER/DATE/BOOL/SELECT）。 */
    public record CreateRequest(Long orgId, String objectType, String fieldKey, String label,
                                String dataType, String options, boolean required, boolean sensitive,
                                boolean aggregatable, Integer seq) {
    }
}
