package com.mandao.grc.modules.aml;

import com.mandao.grc.modules.rbac.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 反洗钱 AML REST 端点：/api/aml（名单管理 + 筛查 + 可疑交易报告 STR）。
 *
 * 隔离：可见范围由 X-User 头决定（切面注入 visible_orgs）；写操作门控资源 "aml"（RW）。
 * actor 取 X-User。合规义务/机构自评复用 obligation、assessment 既有端点，本控制器不重复。
 */
@RestController
@RequestMapping("/api/aml")
public class AmlController {

    private final AmlService service;

    public AmlController(AmlService service) {
        this.service = service;
    }

    // ---- 名单管理 ----

    @GetMapping("/watchlist")
    public List<AmlWatchlist> watchlist() {
        return service.listWatchlist();
    }

    @PostMapping("/watchlist")
    @RequiresPermission("aml")
    public AmlWatchlist addWatch(@RequestBody WatchRequest req,
                                 @RequestHeader(value = "X-User", required = false) String user) {
        return service.addWatchEntry(req.orgId(), req.listType(), req.name(), req.idNumber(),
                req.country(), req.source(), req.reason(), actor(user));
    }

    @PostMapping("/watchlist/{id}/retire")
    @RequiresPermission("aml")
    public AmlWatchlist retireWatch(@PathVariable Long id,
                                    @RequestHeader(value = "X-User", required = false) String user) {
        return service.retireWatchEntry(id, actor(user));
    }

    /** 名单筛查（读）：按名称/证件号匹配本组织 ACTIVE 名单，返回命中项。 */
    @PostMapping("/screen")
    public List<AmlService.ScreenHit> screen(@RequestBody ScreenRequest req) {
        return service.screen(req.name(), req.idNumber());
    }

    // ---- 可疑交易报告 STR ----

    @GetMapping("/str-reports")
    public List<StrReport> strReports() {
        return service.listStr();
    }

    @PostMapping("/str-reports")
    @RequiresPermission("aml")
    public StrReport createStr(@RequestBody StrCreateRequest req,
                               @RequestHeader(value = "X-User", required = false) String user) {
        return service.createStr(req.orgId(), req.subject(), req.amount(), req.riskLevel(),
                req.reason(), req.occurredDate(), actor(user));
    }

    @PostMapping("/str-reports/{id}/submit")
    @RequiresPermission("aml")
    public StrReport submitStr(@PathVariable Long id,
                               @RequestHeader(value = "X-User", required = false) String user) {
        return service.submitStr(id, actor(user));
    }

    @PostMapping("/str-reports/{id}/report")
    @RequiresPermission("aml")
    public StrReport reportStr(@PathVariable Long id, @RequestBody StrReportRequest req,
                               @RequestHeader(value = "X-User", required = false) String user) {
        return service.reportStr(id, req.reportedTo(), req.reportNo(), req.reportedDate(), actor(user));
    }

    @PostMapping("/str-reports/{id}/close")
    @RequiresPermission("aml")
    public StrReport closeStr(@PathVariable Long id,
                              @RequestHeader(value = "X-User", required = false) String user) {
        return service.closeStr(id, actor(user));
    }

    private String actor(String user) {
        return com.mandao.grc.common.auth.ActorResolver.resolve(user);
    }

    public record WatchRequest(Long orgId, String listType, String name, String idNumber,
                               String country, String source, String reason) {
    }

    public record ScreenRequest(String name, String idNumber) {
    }

    public record StrCreateRequest(Long orgId, String subject, BigDecimal amount, String riskLevel,
                                   String reason, LocalDate occurredDate) {
    }

    public record StrReportRequest(String reportedTo, String reportNo, LocalDate reportedDate) {
    }
}
