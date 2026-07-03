package com.mandao.grc.modules.assessment;

import com.mandao.grc.common.auth.CurrentUserContext;
import com.mandao.grc.modules.rbac.RequiresPermission;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
 * 评估过程文档 + RTP REST 端点（V51 · R3）。写门控 "risk"。
 */
@RestController
@RequestMapping("/api")
public class AssessmentDocController {

    private final AssessmentDocService service;

    public AssessmentDocController(AssessmentDocService service) {
        this.service = service;
    }

    // ---------- 过程文档 ----------

    /** 评估的过程文档清单。 */
    @GetMapping("/assessments/{id}/docs")
    public List<AssessmentDoc> listDocs(@PathVariable Long id) {
        return service.listDocs(id);
    }

    /** 上传过程文档（multipart；docType=PLAN/INTERVIEW/REPORT/RTP/ACCEPTANCE/OTHER）。 */
    @PostMapping("/assessments/{id}/docs")
    @RequiresPermission("risk")
    public AssessmentDoc upload(@PathVariable Long id,
                                @RequestParam("file") MultipartFile file,
                                @RequestParam("docType") String docType,
                                @RequestParam("name") String name,
                                @RequestHeader(value = "X-User", required = false) String user) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("未收到文档文件");
        }
        return service.upload(id, docType, name, file.getOriginalFilename(), file.getContentType(),
                file.getBytes(), actorOf(user));
    }

    /** 下载过程文档原件。 */
    @GetMapping("/assessment-docs/{docId}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long docId) {
        AssessmentDoc d = service.getDoc(docId);
        if (d.getData() == null) {
            throw new IllegalStateException("系统登记件无文件字节，请用对应导出端点生成");
        }
        String fn = d.getFileName() == null ? ("doc-" + docId) : d.getFileName();
        return ResponseEntity.ok()
                .header("Content-Type", d.getContentType() == null ? "application/octet-stream" : d.getContentType())
                .header("Content-Disposition", "attachment; filename*=UTF-8''"
                        + URLEncoder.encode(fn, StandardCharsets.UTF_8).replace("+", "%20"))
                .body(d.getData());
    }

    /** 删除过程文档。 */
    @DeleteMapping("/assessment-docs/{docId}")
    @RequiresPermission("risk")
    public void deleteDoc(@PathVariable Long docId,
                          @RequestHeader(value = "X-User", required = false) String user) {
        service.deleteDoc(docId, actorOf(user));
    }

    // ---------- RTP ----------

    /** 评估的处置计划条目清单（按发现）。 */
    @GetMapping("/assessments/{id}/treatments")
    public List<RiskTreatment> listTreatments(@PathVariable Long id) {
        return service.listTreatments(id);
    }

    /** upsert 某发现的处置计划（措施/责任人/期限/资源/预期残余/状态）。 */
    @PutMapping("/risk-findings/{findingId}/rtp")
    @RequiresPermission("risk")
    public RiskTreatment upsertTreatment(@PathVariable Long findingId,
                                         @RequestBody RtpRequest req,
                                         @RequestHeader(value = "X-User", required = false) String user) {
        return service.upsertTreatment(findingId, req.measure(), req.owner(), req.dueDate(),
                req.resource(), req.expectedResidual(), req.status(), actorOf(user));
    }

    /** RTP 汇总导出（.docx，ISO 27001 处置计划文档）。 */
    @GetMapping("/assessments/{id}/rtp.docx")
    public ResponseEntity<byte[]> rtpDocx(@PathVariable Long id) {
        byte[] body = service.buildRtpDocx(id);
        return ResponseEntity.ok()
                .header("Content-Type", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                .header("Content-Disposition", "attachment; filename=\"risk-treatment-plan-" + id + ".docx\"")
                .body(body);
    }

    private String actorOf(String user) {
        String current = CurrentUserContext.get();
        if (current != null && !current.isBlank()) {
            return current;
        }
        return (user == null || user.isBlank()) ? "anonymous" : user;
    }

    /** RTP 请求体（V51）。 */
    public record RtpRequest(String measure, String owner, java.time.LocalDate dueDate,
                             String resource, RiskLevel expectedResidual, String status) {
    }
}
