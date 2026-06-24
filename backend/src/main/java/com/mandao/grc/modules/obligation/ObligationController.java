package com.mandao.grc.modules.obligation;

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
 * 合规清单 REST 端点：/api/obligations。
 *
 * 隔离：可见范围由 X-User 头决定；actor 取 X-User，缺省 anonymous。
 */
@RestController
@RequestMapping("/api/obligations")
public class ObligationController {

    private final ObligationService service;

    public ObligationController(ObligationService service) {
        this.service = service;
    }

    @GetMapping
    public List<Obligation> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public Obligation get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    public Obligation create(@RequestBody CreateObligationRequest req,
                             @RequestHeader(value = "X-User", required = false) String user) {
        return service.create(req.orgId(), req.code(), req.title(), req.sourceRef(), req.category(),
                req.requirement(), req.ownerDept(), req.dueDate(), actor(user));
    }

    /** 开始落实。 */
    @PostMapping("/{id}/start")
    public Obligation start(@PathVariable Long id,
                            @RequestHeader(value = "X-User", required = false) String user) {
        return service.start(id, actor(user));
    }

    /** 标记已落实（须留证据）。 */
    @PostMapping("/{id}/fulfill")
    public Obligation fulfill(@PathVariable Long id,
                              @RequestBody FulfillRequest req,
                              @RequestHeader(value = "X-User", required = false) String user) {
        return service.fulfill(id, req == null ? null : req.evidence(), actor(user));
    }

    /** 标记不合规。 */
    @PostMapping("/{id}/non-compliant")
    public Obligation markNonCompliant(@PathVariable Long id,
                                       @RequestBody(required = false) ReasonRequest req,
                                       @RequestHeader(value = "X-User", required = false) String user) {
        return service.markNonCompliant(id, req == null ? null : req.reason(), actor(user));
    }

    private String actor(String user) {
        return (user == null || user.isBlank()) ? "anonymous" : user;
    }

    /** 登记义务请求体。 */
    public record CreateObligationRequest(Long orgId, String code, String title, String sourceRef,
                                          String category, String requirement, String ownerDept, LocalDate dueDate) {
    }

    /** 落实请求体（证据必填）。 */
    public record FulfillRequest(String evidence) {
    }

    /** 原因请求体。 */
    public record ReasonRequest(String reason) {
    }
}
