package com.mandao.grc.modules.regulatory;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 重大事件报送 REST 端点：/api/major-incidents。
 *
 * 隔离/actor：可见范围由 X-User 头决定（切面注入 visible_orgs），actor 取 X-User。
 * 报送状态机：DRAFT → REPORTED → CLOSED。
 */
@RestController
@RequestMapping("/api/major-incidents")
public class MajorIncidentController {

    private final MajorIncidentService service;

    public MajorIncidentController(MajorIncidentService service) {
        this.service = service;
    }

    @GetMapping
    public List<MajorIncidentReport> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public MajorIncidentReport get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    public MajorIncidentReport create(@RequestBody CreateIncidentRequest req,
                                      @RequestHeader(value = "X-User", required = false) String user) {
        return service.create(req.orgId(), req.title(), req.severity(), req.occurredAt(), actor(user));
    }

    @PostMapping("/{id}/report")
    public MajorIncidentReport report(@PathVariable Long id,
                                      @RequestHeader(value = "X-User", required = false) String user) {
        return service.report(id, actor(user));
    }

    @PostMapping("/{id}/close")
    public MajorIncidentReport close(@PathVariable Long id,
                                     @RequestHeader(value = "X-User", required = false) String user) {
        return service.close(id, actor(user));
    }

    private String actor(String user) {
        return (user == null || user.isBlank()) ? "anonymous" : user;
    }

    /** 新建重大事件报送请求体。 */
    public record CreateIncidentRequest(Long orgId, String title, String severity, OffsetDateTime occurredAt) {
    }
}
