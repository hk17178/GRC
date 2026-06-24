package com.mandao.grc.modules.regulatory;

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
 * 处罚约谈 REST 端点：/api/reg-penalties。
 *
 * 隔离/actor：可见范围由 X-User 头决定（切面注入 visible_orgs），actor 取 X-User。
 * 处置状态机：OPEN → RECTIFYING → CLOSED。
 */
@RestController
@RequestMapping("/api/reg-penalties")
public class RegPenaltyController {

    private final RegPenaltyService service;

    public RegPenaltyController(RegPenaltyService service) {
        this.service = service;
    }

    @GetMapping
    public List<RegPenalty> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public RegPenalty get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    public RegPenalty create(@RequestBody CreatePenaltyRequest req,
                             @RequestHeader(value = "X-User", required = false) String user) {
        return service.create(req.orgId(), req.title(), req.regulator(), req.penaltyType(),
                req.amount(), req.occurredDate(), actor(user));
    }

    @PostMapping("/{id}/rectify")
    public RegPenalty rectify(@PathVariable Long id,
                              @RequestHeader(value = "X-User", required = false) String user) {
        return service.rectify(id, actor(user));
    }

    @PostMapping("/{id}/close")
    public RegPenalty close(@PathVariable Long id,
                            @RequestHeader(value = "X-User", required = false) String user) {
        return service.close(id, actor(user));
    }

    private String actor(String user) {
        return (user == null || user.isBlank()) ? "anonymous" : user;
    }

    /** 新建处罚约谈请求体。 */
    public record CreatePenaltyRequest(Long orgId, String title, String regulator, String penaltyType,
                                       BigDecimal amount, LocalDate occurredDate) {
    }
}
