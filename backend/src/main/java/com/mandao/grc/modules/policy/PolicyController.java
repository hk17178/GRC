package com.mandao.grc.modules.policy;

import com.mandao.grc.modules.rbac.RequiresPermission;
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
 * 制度体系 REST 端点：/api/policies（参照 AssessmentController 风格）。
 *
 * 隔离：与评估端点一致，可见范围由请求经 {@link com.mandao.grc.common.isolation.IsolationFilter}
 * 解析的 X-User 头决定，本控制器不再处理 org 过滤。
 *
 * actor（操作人）暂从请求头 X-User 取（与隔离主体同源）；缺省占位 "anonymous"。
 * 正式鉴权接入后由安全上下文提供。
 */
@RestController
@RequestMapping("/api/policies")
public class PolicyController {

    private final PolicyService service;

    public PolicyController(PolicyService service) {
        this.service = service;
    }

    /** 列出当前主体可见组织范围内的制度。 */
    @GetMapping
    public List<Policy> list() {
        return service.list();
    }

    /** 取单个制度（不可见则视为不存在）。 */
    @GetMapping("/{id}")
    public Policy get(@PathVariable Long id) {
        return service.get(id);
    }

    /** 新建草稿制度。 */
    @PostMapping
    @RequiresPermission("policy.create")
    public Policy create(@RequestBody CreatePolicyRequest req,
                         @RequestHeader(value = "X-User", required = false) String user) {
        return service.create(req.orgId(), req.code(), req.title(), req.content(), actor(user));
    }

    /** 提交评审：DRAFT → REVIEW。 */
    @PostMapping("/{id}/submit")
    @RequiresPermission("policy.submit")
    public Policy submit(@PathVariable Long id,
                         @RequestHeader(value = "X-User", required = false) String user) {
        return service.submitForApproval(id, actor(user));
    }

    /** 审批通过：REVIEW → EFFECTIVE。 */
    @PostMapping("/{id}/approve")
    @RequiresPermission("policy.decide")
    public Policy approve(@PathVariable Long id,
                          @RequestHeader(value = "X-User", required = false) String user) {
        return service.approve(id, actor(user));
    }

    /** 审批驳回：REVIEW → DRAFT。 */
    @PostMapping("/{id}/reject")
    @RequiresPermission("policy.decide")
    public Policy reject(@PathVariable Long id,
                         @RequestBody(required = false) RejectRequest req,
                         @RequestHeader(value = "X-User", required = false) String user) {
        return service.reject(id, actor(user), req == null ? null : req.reason());
    }

    /** 废止：EFFECTIVE → DEPRECATED。 */
    @PostMapping("/{id}/archive")
    @RequiresPermission("policy.signoff")
    public Policy archive(@PathVariable Long id,
                          @RequestHeader(value = "X-User", required = false) String user) {
        return service.archive(id, actor(user));
    }

    /** 签署确认（仅 EFFECTIVE 可签）。 */
    @PostMapping("/{id}/signoff")
    @RequiresPermission("policy.signoff")
    public PolicySignoff signoff(@PathVariable Long id,
                                 @RequestHeader(value = "X-User", required = false) String user) {
        return service.signoff(id, actor(user));
    }

    // ---------- M1 深度：元数据 / 修订与版本历史 / 引用关系 / 签署明细 ----------

    /** 更新元数据（体系分类/生效日期/复审周期/责任部门/责任人）。 */
    @PutMapping("/{id}/meta")
    @RequiresPermission("policy")
    public Policy updateMeta(@PathVariable Long id, @RequestBody MetaRequest req,
                             @RequestHeader(value = "X-User", required = false) String user) {
        return service.updateMeta(id, req.framework(), req.effectiveDate(), req.reviewCycleMonths(),
                req.ownerDept(), req.owner(), actor(user));
    }

    /** 修订（仅 EFFECTIVE）：旧版存快照 → 新内容 v+1 → 回 REVIEW 重走审批。 */
    @PostMapping("/{id}/revise")
    @RequiresPermission("policy.submit")
    public Policy revise(@PathVariable Long id, @RequestBody ReviseRequest req,
                         @RequestHeader(value = "X-User", required = false) String user) {
        return service.revise(id, req.title(), req.content(), req.note(), actor(user));
    }

    /** 版本历史（新→旧）。 */
    @GetMapping("/{id}/versions")
    public List<PolicyVersion> versions(@PathVariable Long id) {
        return service.versions(id);
    }

    /** 上传制度原件 .docx（六轮 #6）：提取全文写 content，原件 sha256 固化留档。 */
    @PostMapping("/{id}/document")
    @RequiresPermission("policy")
    public Policy uploadDocument(@PathVariable Long id,
                                 @org.springframework.web.bind.annotation.RequestParam("file")
                                 org.springframework.web.multipart.MultipartFile file,
                                 @RequestHeader(value = "X-User", required = false) String user)
            throws java.io.IOException {
        return service.uploadDocument(id, file.getOriginalFilename(), file.getBytes(), actor(user));
    }

    /** 下载制度原件（六轮 #6）。 */
    @GetMapping("/{id}/document")
    public org.springframework.http.ResponseEntity<byte[]> downloadDocument(@PathVariable Long id) {
        Policy p = service.getWithDocument(id);
        String fn = java.net.URLEncoder.encode(p.getDocName() == null ? "policy.docx" : p.getDocName(),
                java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
        return org.springframework.http.ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename*=UTF-8''" + fn)
                .header("Content-Type",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                .body(p.getDocBytes());
    }

    /** 添加引用关系。 */
    @PostMapping("/{id}/refs")
    @RequiresPermission("policy")
    public PolicyRef addRef(@PathVariable Long id, @RequestBody RefRequest req,
                            @RequestHeader(value = "X-User", required = false) String user) {
        return service.addRef(id, req.refPolicyId(), req.note(), actor(user));
    }

    /** 引用关系（outgoing=本制度引用了谁 / incoming=谁引用了本制度）。 */
    @GetMapping("/{id}/refs")
    public java.util.Map<String, List<PolicyRef>> refs(@PathVariable Long id) {
        return service.refs(id);
    }

    /** 签署确认明细。 */
    @GetMapping("/{id}/signoffs")
    public List<PolicySignoff> signoffs(@PathVariable Long id) {
        return service.signoffs(id);
    }

    /** actor：优先登录态，其次 X-User，再 anonymous（签署/留痕归真实登录人）。 */
    private String actor(String user) {
        String current = com.mandao.grc.common.auth.CurrentUserContext.get();
        if (current != null && !current.isBlank()) {
            return current;
        }
        return (user == null || user.isBlank()) ? "anonymous" : user;
    }

    /** 新建制度请求体。 */
    public record CreatePolicyRequest(Long orgId, String code, String title, String content) {
    }

    /** 驳回请求体（原因可选）。 */
    public record RejectRequest(String reason) {
    }

    /** 元数据请求体。 */
    public record MetaRequest(String framework, java.time.LocalDate effectiveDate, Integer reviewCycleMonths,
                              String ownerDept, String owner) {
    }

    /** 修订请求体。 */
    public record ReviseRequest(String title, String content, String note) {
    }

    /** 引用请求体。 */
    public record RefRequest(Long refPolicyId, String note) {
    }
}
