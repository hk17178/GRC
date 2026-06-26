package com.mandao.grc.modules.org;

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
 * 组织树 REST 端点：/api/orgs。
 *
 * actor 取 X-User 头；可见范围由切面注入 visible_orgs（但 org 表无 RLS，读全树不受裁剪，
 * 这是组织字典的预期行为——可见域裁剪发生在业务台账 asset/ropa 上）。
 * 建子组织：POST /api/orgs（父组织、code、name、orgType）。
 */
@RestController
@RequestMapping("/api/orgs")
public class OrgController {

    private final OrgService service;

    public OrgController(OrgService service) {
        this.service = service;
    }

    /** 返回某根组织下的子树（默认根 id=1 集团，返回全树）。 */
    @GetMapping("/tree/{rootId}")
    public List<OrgNode> tree(@PathVariable Long rootId) {
        return service.listTree(rootId);
    }

    @GetMapping("/{id}")
    public OrgNode get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @RequiresPermission("org")
    public OrgNode createSubOrg(@RequestBody CreateOrgRequest req,
                               @RequestHeader(value = "X-User", required = false) String user) {
        return service.createSubOrg(req.parentId(), req.code(), req.name(), req.orgType(), actor(user));
    }

    private String actor(String user) {
        return (user == null || user.isBlank()) ? "anonymous" : user;
    }

    /** 新建子组织请求体。 */
    public record CreateOrgRequest(Long parentId, String code, String name, String orgType) {
    }
}
