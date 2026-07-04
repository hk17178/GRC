package com.mandao.grc.modules.audit.management;

import com.mandao.grc.modules.audit.HashChainService;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;

/**
 * 证据库服务（V44，M3 深度）：证据上传/检索/下载/校验 + 卷宗导出。
 *
 * 反向取证：
 *  - 上传即固化 sha256 指纹并写哈希链（EVIDENCE_UPLOAD 留痕）；
 *  - verify(id) 重算指纹与固化值比对——不一致即文件被篡改；
 *  - 证据行携带 plan/finding/remediation 关联，从证据可回溯业务对象。
 *
 * 卷宗导出：按审计计划把 计划信息+发现+整改+证据清单（含指纹）组装成 .docx（POI）。
 *
 * 隔离：方法 @Transactional 且位于 modules 包 → 切面注入 visible_orgs，RLS 裁剪。
 */
@Service
public class EvidenceService {

    private final EvidenceRepository evidenceRepo;
    private final AuditPlanRepository planRepo;
    private final AuditFindingRepository findingRepo;
    private final RemediationOrderRepository remediationRepo;
    private final HashChainService hashChainService;

    public EvidenceService(EvidenceRepository evidenceRepo, AuditPlanRepository planRepo,
                           AuditFindingRepository findingRepo, RemediationOrderRepository remediationRepo,
                           HashChainService hashChainService) {
        this.evidenceRepo = evidenceRepo;
        this.planRepo = planRepo;
        this.findingRepo = findingRepo;
        this.remediationRepo = remediationRepo;
        this.hashChainService = hashChainService;
    }

    /** 上传证据：至少关联 计划/发现/整改单 之一；固化 sha256 并留痕。 */
    @Transactional
    public Evidence upload(Long orgId, Long planId, Long findingId, Long remediationId,
                           String name, String fileName, String contentType, byte[] data, String actor) {
        return upload(orgId, planId, findingId, remediationId, null, null, name, fileName, contentType, data, actor);
    }

    /**
     * 上传证据（七轮 7-2 扩展）：关联对象扩到 报送事项/重大事件——监管回执入证据库，
     * 与审计证据同一套 sha256 固化与留痕，形成完整举证链。
     */
    @Transactional
    public Evidence upload(Long orgId, Long planId, Long findingId, Long remediationId,
                           Long filingId, Long incidentId,
                           String name, String fileName, String contentType, byte[] data, String actor) {
        if (planId == null && findingId == null && remediationId == null && filingId == null && incidentId == null) {
            throw new IllegalArgumentException("证据须至少关联 审计计划/审计发现/整改单/监管报送/重大事件 之一");
        }
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("证据文件为空");
        }
        Evidence e = new Evidence(orgId, planId, findingId, remediationId,
                name, fileName, contentType, data, sha256(data), actor);
        e.attachRegulatory(filingId, incidentId);
        Evidence saved = evidenceRepo.save(e);
        hashChainService.append(orgId, "EVIDENCE_UPLOAD", actor, "EVIDENCE:" + saved.getId(),
                "上传证据「" + name + "」sha256=" + saved.getSha256()
                        + refText(planId, findingId, remediationId)
                        + (filingId == null ? "" : " filing=" + filingId)
                        + (incidentId == null ? "" : " incident=" + incidentId));
        return saved;
    }

    /**
     * 证据列表（七轮 7-2 五维过滤 + 7-8 投影分页）：返回不含文件字节的投影，
     * 单页护栏 500 上限缺省 200；原件字节仅下载/取证按 id 单取。
     */
    @Transactional(readOnly = true)
    public List<EvidenceSummary> listSummaries(Long planId, Long findingId, Long remediationId,
                                               Long filingId, Long incidentId, Integer page, Integer size) {
        int p = page == null || page < 0 ? 0 : page;
        int s = size == null || size <= 0 ? 200 : Math.min(size, 500);
        return evidenceRepo.findSummaries(planId, findingId, remediationId, filingId, incidentId,
                org.springframework.data.domain.PageRequest.of(p, s));
    }

    /** 证据列表：按关联对象过滤（全空=可见范围内全部）。 */
    @Transactional(readOnly = true)
    public List<Evidence> list(Long planId, Long findingId, Long remediationId) {
        if (planId != null) {
            return evidenceRepo.findByPlanIdOrderByIdDesc(planId);
        }
        if (findingId != null) {
            return evidenceRepo.findByFindingIdOrderByIdDesc(findingId);
        }
        if (remediationId != null) {
            return evidenceRepo.findByRemediationIdOrderByIdDesc(remediationId);
        }
        return evidenceRepo.findAllByOrderByIdDesc();
    }

    @Transactional(readOnly = true)
    public Evidence get(Long id) {
        return evidenceRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("证据不存在或不可见：id=" + id));
    }

    /** 反向取证结果：指纹校验 + 关联对象回溯。 */
    public record VerifyResult(Long evidenceId, boolean intact, String storedSha256, String actualSha256,
                               Long planId, String planTitle, Long findingId, String findingTitle,
                               Long remediationId) {
    }

    /** 反向取证：重算指纹与上传时固化值比对，并回溯关联业务对象。 */
    @Transactional(readOnly = true)
    public VerifyResult verify(Long id) {
        Evidence e = get(id);
        String actual = sha256(e.getData());
        String planTitle = e.getPlanId() == null ? null
                : planRepo.findById(e.getPlanId()).map(AuditPlan::getTitle).orElse(null);
        String findingTitle = e.getFindingId() == null ? null
                : findingRepo.findById(e.getFindingId()).map(AuditFinding::getTitle).orElse(null);
        return new VerifyResult(e.getId(), actual.equals(e.getSha256()), e.getSha256(), actual,
                e.getPlanId(), planTitle, e.getFindingId(), findingTitle, e.getRemediationId());
    }

    /**
     * 卷宗导出（.docx）：审计计划全貌 = 计划信息 + 发现清单 + 整改台账 + 证据清单（含 sha256 指纹）。
     */
    @Transactional(readOnly = true)
    public byte[] buildDossier(Long planId) {
        AuditPlan plan = planRepo.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("审计计划不存在或不可见：id=" + planId));
        List<AuditFinding> findings = findingRepo.findAll().stream()
                .filter(f -> planId.equals(f.getAuditPlanId())).toList();
        List<RemediationOrder> remediations = remediationRepo.findAll().stream()
                .filter(r -> findings.stream().anyMatch(f -> f.getId().equals(r.getFindingId()))).toList();
        List<Evidence> evidences = evidenceRepo.findAllByOrderByIdDesc().stream()
                .filter(e -> planId.equals(e.getPlanId())
                        || findings.stream().anyMatch(f -> f.getId().equals(e.getFindingId()))
                        || remediations.stream().anyMatch(r -> r.getId().equals(e.getRemediationId())))
                .toList();

        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XWPFParagraph title = doc.createParagraph();
            title.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun tr = title.createRun();
            tr.setText("审计卷宗 · " + plan.getTitle());
            tr.setBold(true);
            tr.setFontSize(18);

            para(doc, "计划编号：AP-" + plan.getId() + "　类型：" + plan.getAuditType()
                    + "　状态：" + plan.getStatus() + "　开始日：" + plan.getPlanStartDate());
            para(doc, "导出说明：本卷宗由平台生成；证据以 SHA-256 指纹固化，可用「反向取证」校验完整性。");

            heading(doc, "一、审计发现（" + findings.size() + "）");
            XWPFTable ft = doc.createTable();
            headerRow(ft, "编号", "问题", "严重度", "状态");
            for (AuditFinding f : findings) {
                row(ft, "AF-" + f.getId(), nvl(f.getTitle()), String.valueOf(f.getSeverity()), String.valueOf(f.getStatus()));
            }

            heading(doc, "二、整改台账（" + remediations.size() + "）");
            XWPFTable rt = doc.createTable();
            headerRow(rt, "编号", "所属发现", "责任人", "期限", "状态");
            for (RemediationOrder r : remediations) {
                row(rt, "RO-" + r.getId(), "AF-" + r.getFindingId(), nvl(r.getAssignee()),
                        r.getDueDate() == null ? "—" : r.getDueDate().toString(), String.valueOf(r.getStatus()));
            }

            heading(doc, "三、证据清单（" + evidences.size() + "）");
            XWPFTable et = doc.createTable();
            headerRow(et, "编号", "名称", "文件", "SHA-256 指纹", "上传人/时间");
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            for (Evidence e : evidences) {
                row(et, "EV-" + e.getId(), nvl(e.getName()), nvl(e.getFileName()), e.getSha256(),
                        nvl(e.getUploadedBy()) + " " + (e.getUploadedAt() == null ? "" : e.getUploadedAt().format(fmt)));
            }

            doc.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException("卷宗导出失败", ex);
        }
    }

    /**
     * 卷宗打包（.zip）：卷宗 .docx + 全部关联证据原件（文件名前缀 EV-{id}，保留原始扩展名）。
     * 证据原件与 docx 内的 sha256 清单互为印证——收件方可自行重算指纹核验。
     */
    @Transactional(readOnly = true)
    public byte[] buildDossierZip(Long planId) {
        byte[] docx = buildDossier(planId);
        List<AuditFinding> findings = findingRepo.findAll().stream()
                .filter(f -> planId.equals(f.getAuditPlanId())).toList();
        List<RemediationOrder> remediations = remediationRepo.findAll().stream()
                .filter(r -> findings.stream().anyMatch(f -> f.getId().equals(r.getFindingId()))).toList();
        List<Evidence> evidences = evidenceRepo.findAllByOrderByIdDesc().stream()
                .filter(e -> planId.equals(e.getPlanId())
                        || findings.stream().anyMatch(f -> f.getId().equals(e.getFindingId()))
                        || remediations.stream().anyMatch(r -> r.getId().equals(e.getRemediationId())))
                .toList();

        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(bos, StandardCharsets.UTF_8)) {
            zip.putNextEntry(new java.util.zip.ZipEntry("audit-dossier-" + planId + ".docx"));
            zip.write(docx);
            zip.closeEntry();
            for (Evidence e : evidences) {
                String fn = e.getFileName() == null || e.getFileName().isBlank() ? "evidence" : e.getFileName();
                zip.putNextEntry(new java.util.zip.ZipEntry("evidence/EV-" + e.getId() + "-" + fn));
                zip.write(e.getData());
                zip.closeEntry();
            }
            zip.finish();
            return bos.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException("卷宗打包失败", ex);
        }
    }

    // ---------- 内部辅助 ----------

    private static void para(XWPFDocument doc, String text) {
        doc.createParagraph().createRun().setText(text);
    }

    private static void heading(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        XWPFRun r = p.createRun();
        r.setText(text);
        r.setBold(true);
        r.setFontSize(13);
    }

    private static void headerRow(XWPFTable table, String... cells) {
        XWPFTableRow row = table.getRow(0);
        for (int i = 0; i < cells.length; i++) {
            (i == 0 ? row.getCell(0) : row.addNewTableCell()).setText(cells[i]);
        }
    }

    private static void row(XWPFTable table, String... cells) {
        XWPFTableRow row = table.createRow();
        for (int i = 0; i < cells.length && i < row.getTableCells().size(); i++) {
            row.getCell(i).setText(cells[i]);
        }
    }

    private static String nvl(String s) {
        return s == null ? "—" : s;
    }

    private static String refText(Long planId, Long findingId, Long remediationId) {
        StringBuilder sb = new StringBuilder();
        if (planId != null) {
            sb.append(" 计划AP-").append(planId);
        }
        if (findingId != null) {
            sb.append(" 发现AF-").append(findingId);
        }
        if (remediationId != null) {
            sb.append(" 整改RO-").append(remediationId);
        }
        return sb.toString();
    }

    /** SHA-256 十六进制指纹。 */
    static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            // JDK 必含 SHA-256，理论不可达
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
