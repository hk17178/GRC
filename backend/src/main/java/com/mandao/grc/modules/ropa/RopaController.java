package com.mandao.grc.modules.ropa;

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
 * 个人信息处理活动（ROPA）REST 端点：/api/ropa。
 *
 * 隔离/actor：可见范围由 X-User 头决定（切面注入 visible_orgs），actor 取 X-User。
 * 状态机：DRAFT → ACTIVE → RETIRED。
 */
@RestController
@RequestMapping("/api/ropa")
public class RopaController {

    private final RopaService service;

    public RopaController(RopaService service) {
        this.service = service;
    }

    @GetMapping
    public List<Ropa> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public Ropa get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @RequiresPermission("org")
    public Ropa create(@RequestBody CreateRopaRequest req,
                       @RequestHeader(value = "X-User", required = false) String user) {
        return service.create(req.orgId(), req.activityName(), req.purpose(), req.dataCategories(),
                req.legalBasis(), req.crossBorder(), req.retention(),
                req.processingMethod(), req.recipients(), actor(user));
    }

    @PostMapping("/{id}/activate")
    @RequiresPermission("org")
    public Ropa activate(@PathVariable Long id,
                         @RequestHeader(value = "X-User", required = false) String user) {
        return service.activate(id, actor(user));
    }

    @PostMapping("/{id}/retire")
    @RequiresPermission("org")
    public Ropa retire(@PathVariable Long id,
                       @RequestHeader(value = "X-User", required = false) String user) {
        return service.retire(id, actor(user));
    }

    private String actor(String user) {
        return com.mandao.grc.common.auth.ActorResolver.resolve(user); // 七轮 7-4：登录态优先，消除 anonymous 归因
    }

    /** 登记个人信息处理活动请求体（B14：含处理方式/接收方法定字段）。 */
    public record CreateRopaRequest(Long orgId, String activityName, String purpose, String dataCategories,
                                    String legalBasis, boolean crossBorder, String retention,
                                    String processingMethod, String recipients) {
    }
}
