package com.mandao.grc.modules.workflow;

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
 * 流程绑定 REST 端点：/api/process-bindings（D1-8 §八 自定义工作流 H-06）。
 *
 * 隔离：可见范围由 X-User 头决定（切面注入 visible_orgs）；resolve 在 RLS 会话里按上下文选流程，
 * 跨组织绑定永不命中。写门控复用审批流配置权限 "approvalflow.save"。
 */
@RestController
@RequestMapping("/api/process-bindings")
public class ProcessBindingController {

    private final ProcessBindingService service;

    public ProcessBindingController(ProcessBindingService service) {
        this.service = service;
    }

    /** 列出某对象类型的流程绑定。 */
    @GetMapping
    public List<ProcessBinding> list(@RequestParam String objectType) {
        return service.list(objectType);
    }

    /** 按上下文解析应走的流程版本快照（无匹配返回空体）。供业务方发起时固化。 */
    @PostMapping("/resolve")
    public ProcessBindingService.ProcessSnapshot resolve(@RequestBody ResolveRequest req) {
        return service.resolve(req.objectType(), req.context());
    }

    @PostMapping
    @RequiresPermission("approvalflow.save")
    public ProcessBinding create(@RequestBody CreateRequest req,
                                 @RequestHeader(value = "X-User", required = false) String user) {
        return service.create(req.orgId(), req.objectType(), req.name(), req.condition(),
                req.processDefKey(), req.processVersion() == null ? 1 : req.processVersion(),
                req.seq() == null ? 0 : req.seq(), actor(user));
    }

    @PostMapping("/{id}/retire")
    @RequiresPermission("approvalflow.save")
    public ProcessBinding retire(@PathVariable Long id,
                                 @RequestHeader(value = "X-User", required = false) String user) {
        return service.retire(id, actor(user));
    }

    private String actor(String user) {
        return com.mandao.grc.common.auth.ActorResolver.resolve(user);
    }

    /** condition 为声明式 JSON：{predicates:[{field,op,value}]}（AND；空=兜底）。 */
    public record CreateRequest(Long orgId, String objectType, String name, String condition,
                                String processDefKey, Integer processVersion, Integer seq) {
    }

    public record ResolveRequest(String objectType, Map<String, Object> context) {
    }
}
