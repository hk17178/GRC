package com.mandao.grc.modules.notify;

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
 * 自定义通知场景 REST 端点：/api/notification-scenes（D1-8 §九，收官）。
 *
 * 隔离：可见范围由 X-User 头决定（切面注入 visible_orgs）；assemble 只返回本组织场景（RLS），
 * org_scope 限自有子树——不跨子公司广播。写门控复用通知中心权限 "notify"。
 */
@RestController
@RequestMapping("/api/notification-scenes")
public class NotificationSceneController {

    private final NotificationSceneService service;

    public NotificationSceneController(NotificationSceneService service) {
        this.service = service;
    }

    /** 场景库（设计态，全局可装配的场景种类）。 */
    @GetMapping("/defs")
    @RequiresPermission("notify")
    public List<NotifSceneDef> defs() {
        return service.listDefs();
    }

    /** 本组织已装配的运行态场景。 */
    @GetMapping
    @RequiresPermission("notify")
    public List<NotificationScene> list() {
        return service.listScenes();
    }

    /** 某场景的升级链。 */
    @GetMapping("/{id}/escalations")
    @RequiresPermission("notify")
    public List<NotificationEscalation> escalations(@PathVariable Long id) {
        return service.escalationsOf(id);
    }

    /** 试装配：给定事件类型，看本组织哪些场景触发、通知谁、如何升级（M10 消费同此结果）。 */
    @PostMapping("/assemble")
    @RequiresPermission("notify")
    public List<NotificationSceneService.AssembledScene> assemble(@RequestBody AssembleRequest req) {
        return service.assemble(req.eventType());
    }

    @PostMapping
    @RequiresPermission("notify")
    public NotificationScene create(@RequestBody CreateRequest req,
                                    @RequestHeader(value = "X-User", required = false) String user) {
        return service.createScene(req.orgId(), req.sceneDefId(), req.name(), req.recipientRoles(),
                req.template(), req.channelType(), req.orgScope(), req.escalations(), actor(user));
    }

    @PostMapping("/{id}/retire")
    @RequiresPermission("notify")
    public NotificationScene retire(@PathVariable Long id,
                                    @RequestHeader(value = "X-User", required = false) String user) {
        return service.retireScene(id, actor(user));
    }

    private String actor(String user) {
        return com.mandao.grc.common.auth.ActorResolver.resolve(user);
    }

    public record CreateRequest(Long orgId, Long sceneDefId, String name, List<String> recipientRoles,
                                String template, String channelType, String orgScope,
                                List<NotificationSceneService.EscalationInput> escalations) {
    }

    public record AssembleRequest(String eventType) {
    }
}
