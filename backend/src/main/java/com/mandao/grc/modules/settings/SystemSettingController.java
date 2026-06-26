package com.mandao.grc.modules.settings;

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
 * 系统设置 REST 端点：/api/settings。
 *
 * 隔离：可见范围由 X-User 头决定（各租户读写自己的配置）；actor 取 X-User，缺省 anonymous。
 */
@RestController
@RequestMapping("/api/settings")
public class SystemSettingController {

    private final SystemSettingService service;

    public SystemSettingController(SystemSettingService service) {
        this.service = service;
    }

    /** 列出配置项；可按分组过滤。 */
    @GetMapping
    public List<SystemSetting> list(@RequestParam(required = false) String category) {
        return category == null ? service.list() : service.listByCategory(category);
    }

    @GetMapping("/{id}")
    public SystemSetting get(@PathVariable Long id) {
        return service.get(id);
    }

    /** 定义配置项。 */
    @PostMapping
    @RequiresPermission("settings")
    public SystemSetting define(@RequestBody DefineRequest req,
                                @RequestHeader(value = "X-User", required = false) String user) {
        return service.define(req.orgId(), req.key(), req.value(), req.valueType(),
                req.category(), req.description(), req.editable(), actor(user));
    }

    /** 更新配置取值（系统锁定项不可改）。 */
    @PostMapping("/{id}/value")
    @RequiresPermission("settings")
    public SystemSetting update(@PathVariable Long id,
                                @RequestBody UpdateRequest req,
                                @RequestHeader(value = "X-User", required = false) String user) {
        return service.update(id, req == null ? null : req.value(), actor(user));
    }

    private String actor(String user) {
        return (user == null || user.isBlank()) ? "anonymous" : user;
    }

    /** 定义配置请求体。 */
    public record DefineRequest(Long orgId, String key, String value, SettingValueType valueType,
                                String category, String description, boolean editable) {
    }

    /** 更新取值请求体。 */
    public record UpdateRequest(String value) {
    }
}
