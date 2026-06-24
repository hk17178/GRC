package com.mandao.grc.modules.feedback;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 建议与反馈 REST 端点：/api/feedback。
 *
 * 隔离：可见范围由 X-User 头决定；actor/提交人取 X-User，缺省 anonymous。
 */
@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    private final FeedbackService service;

    public FeedbackController(FeedbackService service) {
        this.service = service;
    }

    @GetMapping
    public List<Feedback> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public Feedback get(@PathVariable Long id) {
        return service.get(id);
    }

    /** 提交反馈（提交人取 X-User）。 */
    @PostMapping
    public Feedback submit(@RequestBody SubmitRequest req,
                           @RequestHeader(value = "X-User", required = false) String user) {
        return service.submit(req.orgId(), req.type(), req.title(), req.content(), actor(user), actor(user));
    }

    /** 受理并分派。 */
    @PostMapping("/{id}/triage")
    public Feedback triage(@PathVariable Long id,
                           @RequestBody TriageRequest req,
                           @RequestHeader(value = "X-User", required = false) String user) {
        return service.triage(id, req.handler(), actor(user));
    }

    /** 办结（须填处置结果）。 */
    @PostMapping("/{id}/resolve")
    public Feedback resolve(@PathVariable Long id,
                            @RequestBody ResolveRequest req,
                            @RequestHeader(value = "X-User", required = false) String user) {
        return service.resolve(id, req == null ? null : req.resolution(), actor(user));
    }

    /** 关闭。 */
    @PostMapping("/{id}/close")
    public Feedback close(@PathVariable Long id,
                          @RequestHeader(value = "X-User", required = false) String user) {
        return service.close(id, actor(user));
    }

    /** 驳回。 */
    @PostMapping("/{id}/reject")
    public Feedback reject(@PathVariable Long id,
                           @RequestBody(required = false) ReasonRequest req,
                           @RequestHeader(value = "X-User", required = false) String user) {
        return service.reject(id, req == null ? null : req.reason(), actor(user));
    }

    private String actor(String user) {
        return (user == null || user.isBlank()) ? "anonymous" : user;
    }

    /** 提交反馈请求体。 */
    public record SubmitRequest(Long orgId, FeedbackType type, String title, String content) {
    }

    /** 受理请求体。 */
    public record TriageRequest(String handler) {
    }

    /** 办结请求体（处置结果必填）。 */
    public record ResolveRequest(String resolution) {
    }

    /** 原因请求体。 */
    public record ReasonRequest(String reason) {
    }
}
