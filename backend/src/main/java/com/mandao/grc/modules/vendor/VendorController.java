package com.mandao.grc.modules.vendor;

import com.mandao.grc.modules.assessment.RiskLevel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 第三方供应商 REST 端点：/api/vendors。
 *
 * 隔离：可见范围由 X-User 头决定；actor 取 X-User，缺省 anonymous。
 */
@RestController
@RequestMapping("/api/vendors")
public class VendorController {

    private final VendorService service;

    public VendorController(VendorService service) {
        this.service = service;
    }

    @GetMapping
    public List<Vendor> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public Vendor get(@PathVariable Long id) {
        return service.get(id);
    }

    @GetMapping("/{id}/assessments")
    public List<VendorAssessment> assessments(@PathVariable Long id) {
        return service.listAssessments(id);
    }

    @PostMapping
    public Vendor register(@RequestBody RegisterRequest req,
                           @RequestHeader(value = "X-User", required = false) String user) {
        return service.register(req.orgId(), req.code(), req.name(), req.category(),
                req.contact(), req.criticality(), actor(user));
    }

    /** 评估供应商（登记一次评估并回写风险等级）。 */
    @PostMapping("/{id}/assessments")
    public VendorAssessment assess(@PathVariable Long id,
                                   @RequestBody AssessRequest req,
                                   @RequestHeader(value = "X-User", required = false) String user) {
        return service.assess(id, req.riskLevel(), req.score(), req.conclusion(), actor(user));
    }

    /** 启用供应商（准入门控：须已评估）。 */
    @PostMapping("/{id}/activate")
    public Vendor activate(@PathVariable Long id,
                           @RequestHeader(value = "X-User", required = false) String user) {
        return service.activate(id, actor(user));
    }

    /** 暂停供应商。 */
    @PostMapping("/{id}/suspend")
    public Vendor suspend(@PathVariable Long id,
                          @RequestBody(required = false) ReasonRequest req,
                          @RequestHeader(value = "X-User", required = false) String user) {
        return service.suspend(id, req == null ? null : req.reason(), actor(user));
    }

    /** 恢复供应商。 */
    @PostMapping("/{id}/reactivate")
    public Vendor reactivate(@PathVariable Long id,
                             @RequestHeader(value = "X-User", required = false) String user) {
        return service.reactivate(id, actor(user));
    }

    /** 终止供应商。 */
    @PostMapping("/{id}/terminate")
    public Vendor terminate(@PathVariable Long id,
                            @RequestBody(required = false) ReasonRequest req,
                            @RequestHeader(value = "X-User", required = false) String user) {
        return service.terminate(id, req == null ? null : req.reason(), actor(user));
    }

    private String actor(String user) {
        return (user == null || user.isBlank()) ? "anonymous" : user;
    }

    /** 登记供应商请求体。 */
    public record RegisterRequest(Long orgId, String code, String name, String category,
                                  String contact, String criticality) {
    }

    /** 评估请求体。 */
    public record AssessRequest(RiskLevel riskLevel, Integer score, String conclusion) {
    }

    /** 原因请求体（暂停/终止）。 */
    public record ReasonRequest(String reason) {
    }
}
