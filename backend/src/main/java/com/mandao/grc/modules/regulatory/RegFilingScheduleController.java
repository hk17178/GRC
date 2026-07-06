package com.mandao.grc.modules.regulatory;

import com.mandao.grc.modules.rbac.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 周期性报送计划 REST 端点：/api/reg-filing-schedules（M11 B34）。
 *
 * 隔离/actor：可见范围由 X-User 头决定（切面注入 visible_orgs），actor 取登录态优先。
 * 到期生成由内核到期扫描统一调度，本端点只做 CRUD 与启停。
 */
@RestController
@RequestMapping("/api/reg-filing-schedules")
public class RegFilingScheduleController {

    private final RegFilingScheduleService service;

    public RegFilingScheduleController(RegFilingScheduleService service) {
        this.service = service;
    }

    @GetMapping
    public List<RegFilingSchedule> list() {
        return service.list();
    }

    @PostMapping
    @RequiresPermission("regaffairs")
    public RegFilingSchedule create(@RequestBody CreateRequest req,
                                    @RequestHeader(value = "X-User", required = false) String user) {
        return service.create(req.orgId(), req.title(), req.regulator(),
                RegFilingSchedule.Period.valueOf(req.period()),
                req.leadDays() == null ? 15 : req.leadDays(), req.nextDue(), actor(user));
    }

    @PostMapping("/{id}/enabled")
    @RequiresPermission("regaffairs")
    public RegFilingSchedule setEnabled(@PathVariable Long id, @RequestParam boolean enabled,
                                        @RequestHeader(value = "X-User", required = false) String user) {
        return service.setEnabled(id, enabled, actor(user));
    }

    private String actor(String user) {
        return com.mandao.grc.common.auth.ActorResolver.resolve(user);
    }

    /** 新建周期报送计划请求体（period ∈ MONTHLY/QUARTERLY/ANNUAL）。 */
    public record CreateRequest(Long orgId, String title, String regulator, String period,
                                Integer leadDays, LocalDate nextDue) {
    }
}
