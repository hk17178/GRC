package com.mandao.grc.modules.regulatory;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 报送日历 REST 端点：/api/reg-filings（参照 AuditPlanController 风格）。
 *
 * 隔离/actor：可见范围由 X-User 头决定（切面注入 visible_orgs），actor 取 X-User。
 * 报送生命周期：TO_DRAFT → DRAFTING → SUBMITTED → CLOSED。
 */
@RestController
@RequestMapping("/api/reg-filings")
public class RegFilingController {

    private final RegFilingService service;

    public RegFilingController(RegFilingService service) {
        this.service = service;
    }

    @GetMapping
    public List<RegFiling> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public RegFiling get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    public RegFiling create(@RequestBody CreateFilingRequest req,
                            @RequestHeader(value = "X-User", required = false) String user) {
        return service.create(req.orgId(), req.title(), req.regulator(), req.statutoryDeadline(), actor(user));
    }

    @PostMapping("/{id}/prepare")
    public RegFiling prepare(@PathVariable Long id,
                             @RequestHeader(value = "X-User", required = false) String user) {
        return service.prepare(id, actor(user));
    }

    @PostMapping("/{id}/submit")
    public RegFiling submit(@PathVariable Long id,
                            @RequestHeader(value = "X-User", required = false) String user) {
        return service.submit(id, actor(user));
    }

    @PostMapping("/{id}/close")
    public RegFiling close(@PathVariable Long id,
                           @RequestHeader(value = "X-User", required = false) String user) {
        return service.close(id, actor(user));
    }

    private String actor(String user) {
        return (user == null || user.isBlank()) ? "anonymous" : user;
    }

    /** 新建报送事项请求体。 */
    public record CreateFilingRequest(Long orgId, String title, String regulator, LocalDate statutoryDeadline) {
    }
}
