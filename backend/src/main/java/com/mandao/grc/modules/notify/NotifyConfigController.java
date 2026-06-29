package com.mandao.grc.modules.notify;

import com.mandao.grc.modules.rbac.RequiresPermission;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 通知中心配置端点：/api/notify/configs。写操作门控 "notify"（通知中心菜单写权限）。
 */
@RestController
@RequestMapping("/api/notify/configs")
public class NotifyConfigController {

    private final NotifyConfigService service;

    public NotifyConfigController(NotifyConfigService service) {
        this.service = service;
    }

    /** 按 kind（SCENARIO/RULE/CHANNEL）列出配置。 */
    @GetMapping
    public List<NotifyConfig> list(@RequestParam String kind) {
        return service.listByKind(kind);
    }

    @PostMapping
    @RequiresPermission("notify")
    public NotifyConfig create(@RequestBody ConfigRequest req) {
        return service.create(req.orgId(), req.kind(), req.name(), req.detail());
    }

    @PutMapping("/{id}")
    @RequiresPermission("notify")
    public NotifyConfig update(@PathVariable Long id, @RequestBody ConfigRequest req) {
        return service.update(id, req.name(), req.detail());
    }

    @PutMapping("/{id}/enabled")
    @RequiresPermission("notify")
    public NotifyConfig setEnabled(@PathVariable Long id, @RequestParam boolean enabled) {
        return service.setEnabled(id, enabled);
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("notify")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    /** 配置请求体（detail 为各 kind 专有字段的 JSON 文本）。 */
    public record ConfigRequest(Long orgId, String kind, String name, String detail) {
    }
}
