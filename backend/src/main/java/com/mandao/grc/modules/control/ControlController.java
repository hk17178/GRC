package com.mandao.grc.modules.control;

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
 * 统一控件库 REST 端点：/api/controls。
 *
 * 隔离：可见范围由请求经 {@link com.mandao.grc.common.isolation.IsolationFilter} 解析的 X-User 头决定；
 * 本控制器不处理 org 过滤。actor（操作人）取请求头 X-User；缺省占位 "anonymous"。
 */
@RestController
@RequestMapping("/api/controls")
public class ControlController {

    private final ControlService service;

    public ControlController(ControlService service) {
        this.service = service;
    }

    /** 列出当前主体可见组织范围内的控制项。 */
    @GetMapping
    public List<Control> list() {
        return service.list();
    }

    /** 取单个控制项（不可见则视为不存在）。 */
    @GetMapping("/{id}")
    public Control get(@PathVariable Long id) {
        return service.get(id);
    }

    /** 列出某控制项的框架映射。 */
    @GetMapping("/{id}/mappings")
    public List<ControlFrameworkRef> mappings(@PathVariable Long id) {
        return service.listMappings(id);
    }

    /** 定义一个控制项。 */
    @PostMapping
    @RequiresPermission("risk")
    public Control create(@RequestBody CreateControlRequest req,
                          @RequestHeader(value = "X-User", required = false) String user) {
        return service.create(req.orgId(), req.code(), req.name(), req.description(),
                req.domain(), req.owner(), actor(user));
    }

    /** 为控制项新增一条框架映射。 */
    @PostMapping("/{id}/mappings")
    @RequiresPermission("risk")
    public ControlFrameworkRef addMapping(@PathVariable Long id,
                                          @RequestBody AddMappingRequest req,
                                          @RequestHeader(value = "X-User", required = false) String user) {
        return service.addMapping(id, req.framework(), req.clause(), actor(user));
    }

    /** 停用控制项。 */
    @PostMapping("/{id}/retire")
    @RequiresPermission("risk")
    public Control retire(@PathVariable Long id,
                          @RequestHeader(value = "X-User", required = false) String user) {
        return service.retire(id, actor(user));
    }

    /** actor 占位策略：取 X-User，缺省 anonymous。 */
    private String actor(String user) {
        return com.mandao.grc.common.auth.ActorResolver.resolve(user); // 七轮 7-4：登录态优先，消除 anonymous 归因
    }

    /** 新建控制项请求体。 */
    public record CreateControlRequest(Long orgId, String code, String name, String description,
                                       String domain, String owner) {
    }

    /** 新增框架映射请求体。 */
    public record AddMappingRequest(ControlFramework framework, String clause) {
    }
}
