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
    public List<Kri> list() {
        return service.list();
    }

    /** 取单个 KRI（不可见则视为不存在）。 */
    @GetMapping("/{id}")
    public Kri get(@PathVariable Long id) {
        return service.get(id);
    }

    /** 列出某 KRI 的测量历史（最新在前）。 */
    @GetMapping("/{id}/measurements")
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
        return service.record(id, req.value(), req.note(), actor(user));
    }

    /** actor 占位策略：取 X-User，缺省 anonymous。 */
    private String actor(String user) {
        return (user == null || user.isBlank()) ? "anonymous" : user;
    }

    /** 新建 KRI 请求体。 */
    public record CreateKriRequest(Long orgId, String code, String name, String unit,
                                   KriDirection direction, BigDecimal thresholdWarning,
                                   BigDecimal thresholdCritical, String owner) {
    }

    /** 记录测量请求体。 */
    public record RecordRequest(BigDecimal value, String note) {
    }
}
