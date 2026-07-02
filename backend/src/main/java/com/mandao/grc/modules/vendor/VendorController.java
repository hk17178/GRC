package com.mandao.grc.modules.vendor;

import com.mandao.grc.modules.rbac.RequiresPermission;
import com.mandao.grc.modules.assessment.RiskLevel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
    @RequiresPermission("vendor")
    public Vendor register(@RequestBody RegisterRequest req,
                           @RequestHeader(value = "X-User", required = false) String user) {
        return service.register(req.orgId(), req.code(), req.name(), req.category(),
                req.contact(), req.criticality(), actor(user));
    }

    /** 评估供应商（登记一次评估并回写风险等级）。 */
    @PostMapping("/{id}/assessments")
    @RequiresPermission("vendor")
    public VendorAssessment assess(@PathVariable Long id,
                                   @RequestBody AssessRequest req,
                                   @RequestHeader(value = "X-User", required = false) String user) {
        return service.assess(id, req.riskLevel(), req.score(), req.conclusion(), actor(user));
    }

    /** 启用供应商（准入门控：须已评估）。 */
    @PostMapping("/{id}/activate")
    @RequiresPermission("vendor")
    public Vendor activate(@PathVariable Long id,
                           @RequestHeader(value = "X-User", required = false) String user) {
        return service.activate(id, actor(user));
    }

    /** 暂停供应商。 */
    @PostMapping("/{id}/suspend")
    @RequiresPermission("vendor")
    public Vendor suspend(@PathVariable Long id,
                          @RequestBody(required = false) ReasonRequest req,
                          @RequestHeader(value = "X-User", required = false) String user) {
        return service.suspend(id, req == null ? null : req.reason(), actor(user));
    }

    /** 恢复供应商。 */
    @PostMapping("/{id}/reactivate")
    @RequiresPermission("vendor")
    public Vendor reactivate(@PathVariable Long id,
                             @RequestHeader(value = "X-User", required = false) String user) {
        return service.reactivate(id, actor(user));
    }

    /** 终止供应商。 */
    @PostMapping("/{id}/terminate")
    @RequiresPermission("vendor")
    public Vendor terminate(@PathVariable Long id,
                            @RequestBody(required = false) ReasonRequest req,
                            @RequestHeader(value = "X-User", required = false) String user) {
        return service.terminate(id, req == null ? null : req.reason(), actor(user));
    }

    // ---------- M7 深度：技术安全/DPA / SLA / 事件复评 ----------

    /** 更新技术安全/DPA 合规属性。 */
    @PutMapping("/{id}/compliance")
    @RequiresPermission("vendor")
    public Vendor updateCompliance(@PathVariable Long id, @RequestBody ComplianceRequest req,
                                   @RequestHeader(value = "X-User", required = false) String user) {
        return service.updateCompliance(id, req.dataResidency(), req.pciScope() != null && req.pciScope(),
                req.certifications(), req.dpaSigned() != null && req.dpaSigned(),
                req.crossBorder() != null && req.crossBorder(), req.subProcessing(), actor(user));
    }

    /** 某供应商 SLA 项。 */
    @GetMapping("/{id}/sla")
    public List<VendorSla> listSla(@PathVariable Long id) {
        return service.listSla(id);
    }

    /** 全部 SLA（SLA 跟踪页）。 */
    @GetMapping("/sla")
    public List<VendorSla> listAllSla() {
        return service.listAllSla();
    }

    /** 新增 SLA 项。 */
    @PostMapping("/{id}/sla")
    @RequiresPermission("vendor")
    public VendorSla addSla(@PathVariable Long id, @RequestBody SlaRequest req,
                            @RequestHeader(value = "X-User", required = false) String user) {
        return service.addSla(id, req.item(), req.target(), req.actual(), req.dueDate(),
                req.met() == null || req.met(), actor(user));
    }

    /** 回填 SLA 实际值/达标。 */
    @PutMapping("/sla/{slaId}")
    @RequiresPermission("vendor")
    public VendorSla trackSla(@PathVariable Long slaId, @RequestBody SlaTrackRequest req,
                              @RequestHeader(value = "X-User", required = false) String user) {
        return service.trackSla(slaId, req.actual(), req.met() == null || req.met(), actor(user));
    }

    /** 全部外部事件。 */
    @GetMapping("/incidents")
    public List<VendorIncident> listIncidents() {
        return service.listIncidents();
    }

    /** 登记外部负面事件。 */
    @PostMapping("/{id}/incidents")
    @RequiresPermission("vendor")
    public VendorIncident reportIncident(@PathVariable Long id, @RequestBody IncidentRequest req,
                                         @RequestHeader(value = "X-User", required = false) String user) {
        return service.reportIncident(id, req.event(), req.source(), req.riskLevel(), actor(user));
    }

    /** 事件触发复评（登记 EVENT 类型评估 + 事件转 REASSESSING）。 */
    @PostMapping("/incidents/{incidentId}/reassess")
    @RequiresPermission("vendor")
    public VendorIncident reassess(@PathVariable Long incidentId, @RequestBody AssessRequest req,
                                   @RequestHeader(value = "X-User", required = false) String user) {
        return service.triggerReassess(incidentId, req.riskLevel(), req.score(), req.conclusion(), actor(user));
    }

    /** 事件闭环。 */
    @PostMapping("/incidents/{incidentId}/close")
    @RequiresPermission("vendor")
    public VendorIncident closeIncident(@PathVariable Long incidentId,
                                        @RequestHeader(value = "X-User", required = false) String user) {
        return service.closeIncident(incidentId, actor(user));
    }

    /** actor：优先登录态，其次 X-User，再 anonymous。 */
    private String actor(String user) {
        String current = com.mandao.grc.common.auth.CurrentUserContext.get();
        if (current != null && !current.isBlank()) {
            return current;
        }
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

    // M7 深度请求体
    public record ComplianceRequest(String dataResidency, Boolean pciScope, String certifications,
                                    Boolean dpaSigned, Boolean crossBorder, String subProcessing) {
    }

    public record SlaRequest(String item, String target, String actual, java.time.LocalDate dueDate, Boolean met) {
    }

    public record SlaTrackRequest(String actual, Boolean met) {
    }

    public record IncidentRequest(String event, String source, String riskLevel) {
    }
}
