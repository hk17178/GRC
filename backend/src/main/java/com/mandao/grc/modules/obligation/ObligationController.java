package com.mandao.grc.modules.obligation;

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

    /** 带派生满足状态的列表（八轮 8-3：状态由举证链派生只读）。 */
    @GetMapping("/derived")
    public List<ObligationService.ObligationRow> listDerived() {
        return service.listWithDerived();
    }

    @GetMapping("/{id}")
    public Obligation get(@PathVariable Long id) {
        return service.get(id);
    }

    /** 义务举证链明细（依据弹层）。 */
    @GetMapping("/{id}/links")
    public List<ObligationLink> links(@PathVariable Long id) {
        return service.links(id);
    }

    /** 挂接举证依据（制度/控制/评估/审计/证据）。 */
    @PostMapping("/{id}/links")
    @RequiresPermission("obligation")
    public ObligationLink addLink(@PathVariable Long id, @RequestBody LinkRequest req,
                                  @RequestHeader(value = "X-User", required = false) String user) {
        return service.addLink(id, req.refType(), req.refId(), req.note(), actor(user));
    }

    /** 摘除举证依据。 */
    @org.springframework.web.bind.annotation.DeleteMapping("/links/{linkId}")
    @RequiresPermission("obligation")
    public void removeLink(@PathVariable Long linkId,
                           @RequestHeader(value = "X-User", required = false) String user) {
        service.removeLink(linkId, actor(user));
    }

    /** 举证关联请求体。 */
    public record LinkRequest(String refType, Long refId, String note) {
    }

    @PostMapping
    @RequiresPermission("obligation")
    public Obligation create(@RequestBody CreateObligationRequest req,
                             @RequestHeader(value = "X-User", required = false) String user) {
        return service.create(req.orgId(), req.code(), req.title(), req.sourceRef(), req.category(),
                req.requirement(), req.ownerDept(), req.dueDate(), actor(user));
    }

    /** 开始落实。 */
    @PostMapping("/{id}/start")
    @RequiresPermission("obligation")
    public Obligation start(@PathVariable Long id,
                            @RequestHeader(value = "X-User", required = false) String user) {
        return service.start(id, actor(user));
    }

    /** 标记已落实（须留证据）。 */
    @PostMapping("/{id}/fulfill")
    @RequiresPermission("obligation")
    public Obligation fulfill(@PathVariable Long id,
                              @RequestBody FulfillRequest req,
                              @RequestHeader(value = "X-User", required = false) String user) {
        return service.fulfill(id, req == null ? null : req.evidence(), actor(user));
    }

    /** 标记不合规。 */
    @PostMapping("/{id}/non-compliant")
    @RequiresPermission("obligation")
    public Obligation markNonCompliant(@PathVariable Long id,
                                       @RequestBody(required = false) ReasonRequest req,
                                       @RequestHeader(value = "X-User", required = false) String user) {
        return service.markNonCompliant(id, req == null ? null : req.reason(), actor(user));
    }

    private String actor(String user) {
        return com.mandao.grc.common.auth.ActorResolver.resolve(user); // 七轮 7-4：登录态优先，消除 anonymous 归因
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
