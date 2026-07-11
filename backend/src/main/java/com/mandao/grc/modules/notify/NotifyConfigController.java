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
    private final com.mandao.grc.kernel.NotifyRuleEngine ruleEngine;
    private final com.mandao.grc.kernel.AlertPushService alertPushService;

    public NotifyConfigController(NotifyConfigService service,
                                  com.mandao.grc.kernel.NotifyRuleEngine ruleEngine,
                                  com.mandao.grc.kernel.AlertPushService alertPushService) {
        this.service = service;
        this.ruleEngine = ruleEngine;
        this.alertPushService = alertPushService;
    }

    /** 立即评估一轮全部启用规则（六轮 #7；平时由调度内核每 15 分钟自动跑）。返回本轮新产告警数与外推数。 */
    @PostMapping("/run-engine")
    @RequiresPermission("notify")
    public java.util.Map<String, Integer> runEngine() {
        java.util.List<com.mandao.grc.kernel.AlertPushService.Alert> fresh = new java.util.ArrayList<>();
        int produced = ruleEngine.runOnce(java.time.LocalDate.now(), fresh);
        int pushed = alertPushService.pushAll(fresh); // 八轮 8-1：新告警企微外推（HTTP 在事务外）
        return java.util.Map.of("produced", produced, "pushed", pushed);
    }

    /** 通道外推发送留痕（八轮 8-1：最近 N 条成功/失败记录）。含 webhook 目标与消息，门控 notify 且按可见组织过滤。 */
    @GetMapping("/send-logs")
    @RequiresPermission("notify")
    public java.util.List<java.util.Map<String, Object>> sendLogs(
            @RequestParam(required = false, defaultValue = "100") int limit) {
        return alertPushService.recentLogs(limit);
    }

    /** 按 kind（SCENARIO/RULE/CHANNEL）列出配置。 */
    @GetMapping
    @RequiresPermission("notify")
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
