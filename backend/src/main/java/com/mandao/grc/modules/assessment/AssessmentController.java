package com.mandao.grc.modules.assessment;

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
 * 风险评估 REST 端点：/api/assessments（参照 PolicyController 风格）。
 *
 * 隔离：可见范围由请求经 {@link com.mandao.grc.common.isolation.IsolationFilter} 解析的 X-User 头决定，
 * 本控制器不处理 org 过滤。actor（操作人）取请求头 X-User；缺省占位 "anonymous"。
 */
@RestController
@RequestMapping("/api/assessments")
public class AssessmentController {

    private final AssessmentService service;
    private final com.mandao.grc.common.auth.AppUserRepository userRepo;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    public AssessmentController(AssessmentService service,
                                com.mandao.grc.common.auth.AppUserRepository userRepo,
                                org.springframework.security.crypto.password.PasswordEncoder passwordEncoder) {
        this.service = service;
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
    }

    /** 列出当前主体可见组织范围内的评估。 */
    @GetMapping
    public List<Assessment> list() {
        return service.list();
    }

    /** 取单个评估（不可见则视为不存在）。 */
    @GetMapping("/{id}")
    public Assessment get(@PathVariable Long id) {
        return service.get(id);
    }

    /** 新建草稿评估。 */
    @PostMapping
    @RequiresPermission("risk.create")
    public Assessment create(@RequestBody CreateAssessmentRequest req,
                             @RequestHeader(value = "X-User", required = false) String user) {
        return service.create(req.orgId(), req.title(), req.assessor(), req.period(), req.templateId(), actor(user));
    }

    /** 开始评估：DRAFT → IN_PROGRESS。 */
    @PostMapping("/{id}/start")
    @RequiresPermission("risk")
    public Assessment start(@PathVariable Long id,
                            @RequestHeader(value = "X-User", required = false) String user) {
        return service.start(id, actor(user));
    }

    /** 提交复核：IN_PROGRESS → PENDING_REVIEW。 */
    @PostMapping("/{id}/submit")
    @RequiresPermission("risk")
    public Assessment submit(@PathVariable Long id,
                             @RequestHeader(value = "X-User", required = false) String user) {
        return service.submitForReview(id, actor(user));
    }

    /** 复核驳回：PENDING_REVIEW → IN_PROGRESS。 */
    @PostMapping("/{id}/reject")
    @RequiresPermission("risk")
    public Assessment reject(@PathVariable Long id,
                            @RequestBody(required = false) RejectRequest req,
                            @RequestHeader(value = "X-User", required = false) String user) {
        return service.reject(id, actor(user), req == null ? null : req.reason());
    }

    /** 完成评估：PENDING_REVIEW → COMPLETED（残余高/极高需管理层签批，否则 409）。 */
    @PostMapping("/{id}/complete")
    @RequiresPermission("risk")
    public Assessment complete(@PathVariable Long id,
                               @RequestHeader(value = "X-User", required = false) String user) {
        return service.complete(id, actor(user));
    }

    /**
     * 管理层签批（V55 可信度增强）：
     *  - 身份再认证：须重输登录密码，后端 BCrypt 校验（防止他人用已登录会话冒签）；
     *  - 手写签名存证：canvas 签名 PNG（dataURL）落库 + sha256 入哈希链。
     */
    @PostMapping("/{id}/signoff")
    @RequiresPermission("risk.signoff") // 七轮 7-12：签批细粒度资源——普通安全员(risk RW)不再可签，须管理层/审批角色
    public Assessment signoff(@PathVariable Long id,
                             @RequestBody SignoffRequest req,
                             @RequestHeader(value = "X-User", required = false) String user) {
        String signer = actor(user);
        // 身份再认证：签批人重输密码
        if (req == null || req.password() == null || req.password().isBlank()) {
            throw new IllegalArgumentException("签批须重新输入登录密码进行身份确认");
        }
        var appUser = userRepo.findByUsername(signer)
                .orElseThrow(() -> new IllegalArgumentException("签批人账号不存在：" + signer));
        if (!passwordEncoder.matches(req.password(), appUser.getPasswordHash())) {
            throw new IllegalArgumentException("密码校验失败，签批身份确认未通过");
        }
        // 手写签名（可选但强烈建议）：dataURL → PNG 字节
        byte[] signature = null;
        if (req.signatureDataUrl() != null && !req.signatureDataUrl().isBlank()) {
            String b64 = req.signatureDataUrl();
            int comma = b64.indexOf(',');
            if (comma >= 0) {
                b64 = b64.substring(comma + 1);
            }
            signature = java.util.Base64.getDecoder().decode(b64);
            if (signature.length > 512 * 1024) {
                throw new IllegalArgumentException("签名图过大（>512KB）");
            }
        }
        return service.signOff(id, signer, req.opinion(), req.accepted(), signature);
    }

    /** 查看签批手写签名图（存证核验）。 */
    @GetMapping("/{id}/signature")
    public org.springframework.http.ResponseEntity<byte[]> signature(@PathVariable Long id) {
        Assessment a = service.get(id);
        if (a.getMgmtSignature() == null) {
            return org.springframework.http.ResponseEntity.notFound().build();
        }
        return org.springframework.http.ResponseEntity.ok()
                .header("Content-Type", "image/png")
                .body(a.getMgmtSignature());
    }

    /**
     * actor 归属策略：优先取登录态（JWT Cookie 解析出的当前用户），其次兼容旧 X-User 头，再缺省 anonymous。
     *
     * R1 认证切到 httpOnly Cookie 后前端不再发 X-User，签批/留痕等审计归属须取真实登录人。
     */
    private String actor(String user) {
        String current = com.mandao.grc.common.auth.CurrentUserContext.get();
        if (current != null && !current.isBlank()) {
            return current;
        }
        return com.mandao.grc.common.auth.ActorResolver.resolve(user); // 七轮 7-4：登录态优先，消除 anonymous 归因
    }

    /** 背景建立（V46）：写入/更新评估元数据（范围/目的/依据/方法/准则/评估组/起止）。终态冻结。 */
    @PutMapping("/{id}/context")
    @RequiresPermission("risk")
    public Assessment setContext(@PathVariable Long id,
                                 @RequestBody ContextRequest req,
                                 @RequestHeader(value = "X-User", required = false) String user) {
        return service.setContext(id, req.scope(), req.objective(), req.basis(), req.methods(),
                req.criteria(), req.team(), req.startDate(), req.endDate(), actor(user));
    }

    /** 新建评估请求体（templateId 可空：关联来源模板则启用表单引擎填写）。 */
    public record CreateAssessmentRequest(Long orgId, String title, String assessor, String period, Long templateId) {
    }

    /** 删除草稿评估（UAT 五轮 #1：仅 DRAFT，级联清理+留痕）。 */
    @org.springframework.web.bind.annotation.DeleteMapping("/{id}")
    @RequiresPermission("risk")
    public void deleteDraft(@PathVariable Long id,
                            @RequestHeader(value = "X-User", required = false) String user) {
        service.deleteDraft(id, actor(user));
    }

    /** 作废评估（软删）：IN_PROGRESS/PENDING_REVIEW → CANCELLED。 */
    @PostMapping("/{id}/cancel")
    @RequiresPermission("risk")
    public Assessment cancel(@PathVariable Long id,
                             @RequestBody(required = false) CancelRequest req,
                             @RequestHeader(value = "X-User", required = false) String user) {
        return service.cancel(id, req == null ? null : req.reason(), actor(user));
    }

    /** 作废请求体。 */
    public record CancelRequest(String reason) {
    }

    /** 背景建立请求体（V46）。 */
    public record ContextRequest(String scope, String objective, String basis, String methods,
                                 String criteria, String team,
                                 java.time.LocalDate startDate, java.time.LocalDate endDate) {
    }

    // ===== 评估范围资产（V48 · R2）=====

    /** 范围资产清单（携资产名/类型）。 */
    @GetMapping("/{id}/assets")
    public List<AssessmentService.ScopeAssetView> listScopeAssets(@PathVariable Long id) {
        return service.listScopeAssets(id);
    }

    /** 勾选资产进评估范围（幂等）。 */
    @PostMapping("/{id}/assets")
    @RequiresPermission("risk")
    public AssessmentAsset addScopeAsset(@PathVariable Long id,
                                         @RequestBody ScopeAssetRequest req,
                                         @RequestHeader(value = "X-User", required = false) String user) {
        return service.addScopeAsset(id, req.assetId(), actor(user));
    }

    /** 从评估范围移除资产。 */
    @org.springframework.web.bind.annotation.DeleteMapping("/{id}/assets/{linkId}")
    @RequiresPermission("risk")
    public void removeScopeAsset(@PathVariable Long id, @PathVariable Long linkId,
                                 @RequestHeader(value = "X-User", required = false) String user) {
        service.removeScopeAsset(id, linkId, actor(user));
    }

    /** 范围资产请求体（V48）。 */
    public record ScopeAssetRequest(Long assetId) {
    }

    /** 驳回请求体（原因可选）。 */
    public record RejectRequest(String reason) {
    }

    /** 管理层签批请求体（V55：password 身份再认证必填；signatureDataUrl 手写签名 PNG dataURL 可选）。 */
    public record SignoffRequest(String opinion, boolean accepted, String password, String signatureDataUrl) {
    }
}
