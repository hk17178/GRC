package com.mandao.grc.modules.kri;

import com.mandao.grc.modules.rbac.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * KRI 监控 REST 端点：/api/kris。
 *
 * 隔离：可见范围由请求经 {@link com.mandao.grc.common.isolation.IsolationFilter} 解析的 X-User 头决定；
 * 本控制器不处理 org 过滤。actor（操作人）取请求头 X-User；缺省占位 "anonymous"。
 */
@RestController
@RequestMapping("/api/kris")
public class KriController {

    private final KriService service;

    public KriController(KriService service) {
        this.service = service;
    }

    /** 列出当前主体可见组织范围内的 KRI。 */
    @GetMapping
    @RequiresPermission("risk")
    public List<Kri> list() {
        return service.list();
    }

    /** 取单个 KRI（不可见则视为不存在）。 */
    @GetMapping("/{id}")
    @RequiresPermission("risk")
    public Kri get(@PathVariable Long id) {
        return service.get(id);
    }

    /** 列出某 KRI 的测量历史（最新在前）。 */
    @GetMapping("/{id}/measurements")
    @RequiresPermission("risk")
    public List<KriMeasurement> measurements(@PathVariable Long id) {
        return service.listMeasurements(id);
    }

    /** 定义一个 KRI。 */
    @PostMapping
    @RequiresPermission("risk")
    public Kri create(@RequestBody CreateKriRequest req,
                      @RequestHeader(value = "X-User", required = false) String user) {
        return service.create(req.orgId(), req.code(), req.name(), req.unit(), req.direction(),
                req.thresholdWarning(), req.thresholdCritical(), req.owner(), actor(user));
    }

    /** 记录一次测量（按阈值评定状态并回写最近态）。 */
    @PostMapping("/{id}/measurements")
    @RequiresPermission("risk")
    public KriMeasurement record(@PathVariable Long id,
                                 @RequestBody RecordRequest req,
                                 @RequestHeader(value = "X-User", required = false) String user) {
        if (req == null || req.value() == null) {   // L-1：测量值空值校验，防 NPE/500
            throw new IllegalArgumentException("测量值不能为空");
        }
        return service.record(id, req.value(), req.note(), actor(user));
    }

    /**
     * B39：外部监测系统按 code 批量推送 KRI 测量（M2M 摄入）。逐条独立成败，返回每条结果。
     * 复用会话认证与 org 隔离（orgId 须在可见域内）；真正的 API-Key 机器鉴权列入安全评审项。
     */
    @PostMapping("/push")
    @RequiresPermission("risk")
    public List<KriService.PushResult> push(@RequestBody PushRequest req,
                                            @RequestHeader(value = "X-User", required = false) String user) {
        return service.pushBatch(req.orgId(), req.items(), actor(user));
    }

    /** actor 占位策略：取 X-User，缺省 anonymous。 */
    private String actor(String user) {
        return com.mandao.grc.common.auth.ActorResolver.resolve(user); // 七轮 7-4：登录态优先，消除 anonymous 归因
    }

    /** 新建 KRI 请求体。 */
    public record CreateKriRequest(Long orgId, String code, String name, String unit,
                                   KriDirection direction, BigDecimal thresholdWarning,
                                   BigDecimal thresholdCritical, String owner) {
    }

    /** 记录测量请求体。 */
    public record RecordRequest(BigDecimal value, String note) {
    }

    /** B39 批量推送请求体：{orgId, items:[{code,value,note}]}。 */
    public record PushRequest(Long orgId, List<KriService.PushItem> items) {
    }
}
