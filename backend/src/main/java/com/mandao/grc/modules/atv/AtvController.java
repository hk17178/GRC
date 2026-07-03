package com.mandao.grc.modules.atv;

import com.mandao.grc.modules.rbac.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * A-T-V REST 端点：威胁库 /api/threats、脆弱性库 /api/vulnerabilities、风险场景 /api/risk-scenarios。
 *
 * 隔离：可见范围由 X-User 头决定；actor 取 X-User，缺省 anonymous。
 */
@RestController
@RequestMapping("/api")
public class AtvController {

    private final AtvService service;

    public AtvController(AtvService service) {
        this.service = service;
    }

    // ---------- 威胁库 ----------

    @GetMapping("/threats")
    public List<Threat> listThreats() {
        return service.listThreats();
    }

    @PostMapping("/threats")
    @RequiresPermission("risk")
    public Threat createThreat(@RequestBody ThreatRequest req,
                               @RequestHeader(value = "X-User", required = false) String user) {
        return service.createThreat(req.orgId(), req.code(), req.name(), req.category(), req.description(), actor(user));
    }

    // ---------- 脆弱性库 ----------

    @GetMapping("/vulnerabilities")
    public List<Vulnerability> listVulnerabilities() {
        return service.listVulnerabilities();
    }

    @PostMapping("/vulnerabilities")
    @RequiresPermission("risk")
    public Vulnerability createVulnerability(@RequestBody VulnerabilityRequest req,
                                             @RequestHeader(value = "X-User", required = false) String user) {
        return service.createVulnerability(req.orgId(), req.code(), req.name(), req.category(), req.description(), actor(user));
    }

    // ---------- A-T-V 风险场景 ----------

    /** 列出风险场景；可按 assetId 过滤。 */
    @GetMapping("/risk-scenarios")
    public List<RiskScenario> listScenarios(@RequestParam(required = false) Long assetId) {
        return assetId == null ? service.listScenarios() : service.listScenariosByAsset(assetId);
    }

    @GetMapping("/risk-scenarios/{id}")
    public RiskScenario getScenario(@PathVariable Long id) {
        return service.getScenario(id);
    }

    @PostMapping("/risk-scenarios")
    @RequiresPermission("risk")
    public RiskScenario createScenario(@RequestBody ScenarioRequest req,
                                       @RequestHeader(value = "X-User", required = false) String user) {
        return service.createScenario(req.assetId(), req.threatId(), req.vulnerabilityId(),
                req.likelihood(), req.impact(), req.description(), actor(user));
    }

    /** 重评风险场景（更新可能性/影响并重算固有等级）。 */
    @PostMapping("/risk-scenarios/{id}/reassess")
    @RequiresPermission("risk")
    public RiskScenario reassess(@PathVariable Long id,
                                 @RequestBody ReassessRequest req,
                                 @RequestHeader(value = "X-User", required = false) String user) {
        return service.reassess(id, req.likelihood(), req.impact(), actor(user));
    }

    /** 场景一键生成风险发现（V48 风险登记册：同一评估同一场景只生成一次）。 */
    @PostMapping("/risk-scenarios/{id}/to-finding")
    @RequiresPermission("risk")
    public com.mandao.grc.modules.assessment.RiskFinding toFinding(@PathVariable Long id,
                                                                   @RequestBody ToFindingRequest req,
                                                                   @RequestHeader(value = "X-User", required = false) String user) {
        return service.toFinding(id, req.assessmentId(), actor(user));
    }

    /** 场景生成发现请求体。 */
    public record ToFindingRequest(Long assessmentId) {
    }

    private String actor(String user) {
        return (user == null || user.isBlank()) ? "anonymous" : user;
    }

    /** 威胁登记请求体。 */
    public record ThreatRequest(Long orgId, String code, String name, String category, String description) {
    }

    /** 脆弱性登记请求体。 */
    public record VulnerabilityRequest(Long orgId, String code, String name, String category, String description) {
    }

    /** 风险场景登记请求体。 */
    public record ScenarioRequest(Long assetId, Long threatId, Long vulnerabilityId,
                                  int likelihood, int impact, String description) {
    }

    /** 重评请求体。 */
    public record ReassessRequest(int likelihood, int impact) {
    }
}
