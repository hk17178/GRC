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
 * 监管问询 REST 端点：/api/reg-inquiries。
 *
 * 隔离/actor：可见范围由 X-User 头决定（切面注入 visible_orgs），actor 取 X-User。
 * 处置状态机：DRAFTING → REPLIED → AWAIT_FEEDBACK → CLOSED。
 */
@RestController
@RequestMapping("/api/reg-inquiries")
public class RegInquiryController {

    private final RegInquiryService service;

    public RegInquiryController(RegInquiryService service) {
        this.service = service;
    }

    @GetMapping
    public List<RegInquiry> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public RegInquiry get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    public RegInquiry create(@RequestBody CreateInquiryRequest req,
                             @RequestHeader(value = "X-User", required = false) String user) {
        return service.create(req.orgId(), req.title(), req.regulator(),
                req.receivedDate(), req.dueDate(), actor(user));
    }

    @PostMapping("/{id}/reply")
    public RegInquiry reply(@PathVariable Long id,
                            @RequestHeader(value = "X-User", required = false) String user) {
        return service.reply(id, actor(user));
    }

    @PostMapping("/{id}/await-feedback")
    public RegInquiry awaitFeedback(@PathVariable Long id,
                                    @RequestHeader(value = "X-User", required = false) String user) {
        return service.awaitFeedback(id, actor(user));
    }

    @PostMapping("/{id}/close")
    public RegInquiry close(@PathVariable Long id,
                            @RequestHeader(value = "X-User", required = false) String user) {
        return service.close(id, actor(user));
    }

    private String actor(String user) {
        return (user == null || user.isBlank()) ? "anonymous" : user;
    }

    /** 新建监管问询请求体。 */
    public record CreateInquiryRequest(Long orgId, String title, String regulator,
                                       LocalDate receivedDate, LocalDate dueDate) {
    }
}
