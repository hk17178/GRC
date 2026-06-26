package com.mandao.grc.modules.regulation;

import com.mandao.grc.modules.rbac.RequiresPermission;
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
 * 法规跟踪 REST 端点：/api/regulations。
 *
 * 隔离：可见范围由 X-User 头决定；actor 取 X-User，缺省 anonymous。
 */
@RestController
@RequestMapping("/api/regulations")
public class RegulationController {

    private final RegulationService service;

    public RegulationController(RegulationService service) {
        this.service = service;
    }

    @GetMapping
    public List<Regulation> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public Regulation get(@PathVariable Long id) {
        return service.get(id);
    }

    @GetMapping("/{id}/changes")
    public List<RegulationChange> changes(@PathVariable Long id) {
        return service.listChanges(id);
    }

    @PostMapping
    @RequiresPermission("law")
    public Regulation create(@RequestBody CreateRegulationRequest req,
                             @RequestHeader(value = "X-User", required = false) String user) {
        return service.create(req.orgId(), req.code(), req.title(), req.issuer(), req.category(),
                req.effectiveDate(), req.summary(), actor(user));
    }

    /** 更新法规状态（EFFECTIVE/SUPERSEDED/ABOLISHED）。 */
    @PostMapping("/{id}/status")
    @RequiresPermission("law")
    public Regulation updateStatus(@PathVariable Long id,
                                   @RequestBody StatusRequest req,
                                   @RequestHeader(value = "X-User", required = false) String user) {
        return service.updateStatus(id, req.status(), actor(user));
    }

    /** 登记法规变更动态。 */
    @PostMapping("/{id}/changes")
    @RequiresPermission("law")
    public RegulationChange recordChange(@PathVariable Long id,
                                         @RequestBody ChangeRequest req,
                                         @RequestHeader(value = "X-User", required = false) String user) {
        return service.recordChange(id, req.changeType(), req.changeDate(), req.description(), actor(user));
    }

    /** 完成法规变更的影响评估。 */
    @PostMapping("/changes/{changeId}/assess")
    @RequiresPermission("law")
    public RegulationChange assessImpact(@PathVariable Long changeId,
                                         @RequestBody AssessRequest req,
                                         @RequestHeader(value = "X-User", required = false) String user) {
        return service.assessImpact(changeId, req.impactScope(), req.impactNote(), actor(user));
    }

    private String actor(String user) {
        return (user == null || user.isBlank()) ? "anonymous" : user;
    }

    /** 登记法规请求体。 */
    public record CreateRegulationRequest(Long orgId, String code, String title, String issuer,
                                          String category, LocalDate effectiveDate, String summary) {
    }

    /** 更新状态请求体。 */
    public record StatusRequest(RegulationStatus status) {
    }

    /** 登记变更请求体。 */
    public record ChangeRequest(ChangeType changeType, LocalDate changeDate, String description) {
    }

    /** 影响评估请求体。 */
    public record AssessRequest(String impactScope, String impactNote) {
    }
}
