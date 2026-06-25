package com.mandao.grc.modules.workflow;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 审批流配置 REST 端点：/api/approval-flows（供 Vue Flow 可视化画布读写、发布）。
 *
 * 隔离：可见范围由 X-User 头经 IsolationFilter 解析；各组织只读写自己的审批流。
 * actor 取 X-User（缺省 anonymous）。
 */
@RestController
@RequestMapping("/api/approval-flows")
public class ApprovalFlowController {

    private final ApprovalFlowService service;
    private final ApprovalEngine engine;

    public ApprovalFlowController(ApprovalFlowService service, ApprovalEngine engine) {
        this.service = service;
        this.engine = engine;
    }

    /** 列出审批流（可按业务类型过滤）。 */
    @GetMapping
    public List<ApprovalFlow> list(@RequestParam(required = false) ApprovalBizType bizType) {
        return service.list(bizType);
    }

    /** 取单个审批流（含画布图）。 */
    @GetMapping("/{id}")
    public ApprovalFlow get(@PathVariable Long id) {
        return service.get(id);
    }

    /** 新建草稿。 */
    @PostMapping
    public ApprovalFlow create(@RequestBody CreateFlowRequest req) {
        return service.createDraft(req.orgId(), req.bizType(), req.name(), req.graph());
    }

    /** 更新草稿（名称 + 画布）。 */
    @PutMapping("/{id}")
    public ApprovalFlow update(@PathVariable Long id, @RequestBody UpdateFlowRequest req) {
        return service.updateDraft(id, req.name(), req.graph());
    }

    /** 校验画布结构（不发布）。 */
    @PostMapping("/{id}/validate")
    public Map<String, Object> validate(@PathVariable Long id) {
        service.validate(id);
        return Map.of("valid", true);
    }

    /** 发布：校验 → 编译 BPMN → 部署 → 置 ACTIVE。 */
    @PostMapping("/{id}/publish")
    public ApprovalFlow publish(@PathVariable Long id) {
        return service.publish(id);
    }

    /** 停用。 */
    @PostMapping("/{id}/retire")
    public ApprovalFlow retire(@PathVariable Long id) {
        return service.retire(id);
    }

    /** 灌默认单节点流并发布（初始迁移用）。 */
    @PostMapping("/seed-default")
    public ApprovalFlow seedDefault(@RequestBody SeedRequest req) {
        return service.seedDefault(req.orgId(), req.bizType(), req.approverRole() == null ? "CHECKER" : req.approverRole());
    }

    // ---------- 请求体 ----------

    public record CreateFlowRequest(Long orgId, ApprovalBizType bizType, String name, FlowGraph graph) {
    }

    public record UpdateFlowRequest(String name, FlowGraph graph) {
    }

    public record SeedRequest(Long orgId, ApprovalBizType bizType, String approverRole) {
    }
}
