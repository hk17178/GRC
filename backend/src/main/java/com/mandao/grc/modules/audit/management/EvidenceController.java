package com.mandao.grc.modules.audit.management;

import com.mandao.grc.common.auth.CurrentUserContext;
import com.mandao.grc.modules.rbac.RequiresPermission;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 证据库 REST 端点（V44）：/api/evidence + 卷宗导出 /api/audit-plans/{id}/dossier。
 *
 * 隔离：可见范围由登录人决定（切面注入 visible_orgs）；上传写门控 "extaudit"（审计管理写权限）。
 */
@RestController
@RequestMapping("/api")
public class EvidenceController {

    private final EvidenceService service;

    public EvidenceController(EvidenceService service) {
        this.service = service;
    }

    /** 证据列表（可按 planId/findingId/remediationId 过滤）。 */
    @GetMapping("/evidence")
    @RequiresPermission("extaudit")
    public List<EvidenceSummary> list(@RequestParam(required = false) Long planId,
                               @RequestParam(required = false) Long findingId,
                               @RequestParam(required = false) Long remediationId,
                               @RequestParam(required = false) Long filingId,
                               @RequestParam(required = false) Long incidentId,
                               @RequestParam(required = false) Long inquiryId,
                               @RequestParam(required = false) Long penaltyId,
                               @RequestParam(required = false) Integer page,
                               @RequestParam(required = false) Integer size) {
        // 七轮 7-8 + M11 B13：投影不带文件字节 + 七维过滤 + 分页护栏
        return service.listSummaries(planId, findingId, remediationId, filingId, incidentId,
                inquiryId, penaltyId, page, size);
    }

    /** 上传证据（multipart；至少关联 计划/发现/整改单/报送/事件/问询/处罚 之一）。 */
    @PostMapping("/evidence")
    @RequiresPermission("extaudit")
    public Evidence upload(@RequestParam("file") MultipartFile file,
                           @RequestParam("name") String name,
                           @RequestParam("orgId") Long orgId,
                           @RequestParam(required = false) Long planId,
                           @RequestParam(required = false) Long findingId,
                           @RequestParam(required = false) Long remediationId,
                           @RequestParam(required = false) Long filingId,
                           @RequestParam(required = false) Long incidentId,
                           @RequestParam(required = false) Long inquiryId,
                           @RequestParam(required = false) Long penaltyId,
                           @RequestHeader(value = "X-User", required = false) String user) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("未收到证据文件");
        }
        // 七轮 7-2 + M11 B13：支持报送回执/事件/问询回函/处罚整改材料入证据库
        return service.upload(orgId, planId, findingId, remediationId, filingId, incidentId, inquiryId, penaltyId,
                name, file.getOriginalFilename(), file.getContentType(), file.getBytes(), actorOf(user));
    }

    /** 下载证据原文件。 */
    @GetMapping("/evidence/{id}/download")
    @RequiresPermission("extaudit")
    public ResponseEntity<byte[]> download(@PathVariable Long id) {
        Evidence e = service.get(id);
        String fn = e.getFileName() == null ? ("evidence-" + id) : e.getFileName();
        return ResponseEntity.ok()
                .header("Content-Type", e.getContentType() == null ? "application/octet-stream" : e.getContentType())
                .header("Content-Disposition", "attachment; filename*=UTF-8''"
                        + URLEncoder.encode(fn, StandardCharsets.UTF_8).replace("+", "%20"))
                .body(e.getData());
    }

    /** 反向取证：指纹校验 + 关联对象回溯。 */
    @GetMapping("/evidence/{id}/verify")
    @RequiresPermission("extaudit")
    public EvidenceService.VerifyResult verify(@PathVariable Long id) {
        return service.verify(id);
    }

    /** 卷宗导出（.docx）：计划信息 + 发现清单 + 整改台账 + 证据指纹清单。 */
    @GetMapping("/audit-plans/{id}/dossier")
    @RequiresPermission("extaudit")
    public ResponseEntity<byte[]> dossier(@PathVariable Long id) {
        byte[] body = service.buildDossier(id);
        return ResponseEntity.ok()
                .header("Content-Type", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                .header("Content-Disposition", "attachment; filename=\"audit-dossier-" + id + ".docx\"")
                .body(body);
    }

    /**
     * 卷宗打包（.zip）：卷宗 .docx + 全部关联证据原件（与 docx 内 sha256 清单互为印证）。
     * 架构治理包 B31：改流式写响应体——StreamingResponseBody 直写，证据字节逐个单取，不在内存组装整包。
     */
    @GetMapping("/audit-plans/{id}/dossier.zip")
    @RequiresPermission("extaudit")
    public ResponseEntity<org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody> dossierZip(
            @PathVariable Long id) {
        org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody body =
                out -> service.streamDossierZip(id, out);
        return ResponseEntity.ok()
                .header("Content-Type", "application/zip")
                .header("Content-Disposition", "attachment; filename=\"audit-dossier-" + id + ".zip\"")
                .body(body);
    }

    private String actorOf(String user) {
        return CurrentUserContext.get() != null ? CurrentUserContext.get()
                : (user == null || user.isBlank() ? "anonymous" : user);
    }
}
