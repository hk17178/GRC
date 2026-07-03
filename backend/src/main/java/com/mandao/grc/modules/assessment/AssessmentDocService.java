package com.mandao.grc.modules.assessment;

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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * 评估过程文档中心 + RTP（V51 · R3）。
 *
 * 过程文档：上传（sha256 固化+留痕）/清单/下载/删除；
 * RTP：一发现一计划的 upsert，评估级汇总导出 .docx（措施/责任人/期限/资源/预期残余）。
 *
 * 隔离：方法 @Transactional 且位于 modules 包 → 切面注入 visible_orgs，RLS 裁剪。
 */
@Service
public class AssessmentDocService {

    private static final Map<RiskLevel, String> LV_ZH = Map.of(
            RiskLevel.VERY_LOW, "极低", RiskLevel.LOW, "低", RiskLevel.MID, "中",
            RiskLevel.HIGH, "高", RiskLevel.VERY_HIGH, "极高");

    private final AssessmentDocRepository docRepo;
    private final AssessmentRepository assessmentRepo;
    private final RiskFindingRepository findingRepo;
    private final RiskTreatmentRepository treatmentRepo;
    private final HashChainService hashChainService;

    public AssessmentDocService(AssessmentDocRepository docRepo, AssessmentRepository assessmentRepo,
                                RiskFindingRepository findingRepo, RiskTreatmentRepository treatmentRepo,
                                HashChainService hashChainService) {
        this.docRepo = docRepo;
        this.assessmentRepo = assessmentRepo;
        this.findingRepo = findingRepo;
        this.treatmentRepo = treatmentRepo;
        this.hashChainService = hashChainService;
    }

    // ---------- 过程文档 ----------

    @Transactional(readOnly = true)
    public List<AssessmentDoc> listDocs(Long assessmentId) {
        return docRepo.findByAssessmentIdOrderByIdDesc(assessmentId);
    }

    /** 上传过程文档（sha256 固化 + 哈希链留痕）。 */
    @Transactional
    public AssessmentDoc upload(Long assessmentId, String docType, String name,
                                String fileName, String contentType, byte[] data, String actor) {
        Assessment a = assessmentRepo.findById(assessmentId)
                .orElseThrow(() -> new IllegalArgumentException("评估不存在或不可见：id=" + assessmentId));
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("文档文件为空");
        }
        AssessmentDoc saved = docRepo.save(new AssessmentDoc(a.getOrgId(), assessmentId, docType, name,
                fileName, contentType, data, sha256(data), actor));
        hashChainService.append(a.getOrgId(), "ASSESS_DOC_UPLOAD", actor, "ASSESS_DOC:" + saved.getId(),
                "上传评估过程文档「" + name + "」type=" + docType + " sha256=" + saved.getSha256());
        return saved;
    }

    @Transactional(readOnly = true)
    public AssessmentDoc getDoc(Long id) {
        return docRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("过程文档不存在或不可见：id=" + id));
    }

    /** 删除过程文档（留痕）。 */
    @Transactional
    public void deleteDoc(Long id, String actor) {
        AssessmentDoc d = getDoc(id);
        docRepo.delete(d);
        hashChainService.append(d.getOrgId(), "ASSESS_DOC_DELETE", actor, "ASSESS_DOC:" + id,
                "删除评估过程文档「" + d.getName() + "」");
    }

    // ---------- RTP（一发现一计划） ----------

    @Transactional(readOnly = true)
    public List<RiskTreatment> listTreatments(Long assessmentId) {
        List<Long> ids = findingRepo.findByAssessmentId(assessmentId).stream().map(RiskFinding::getId).toList();
        return ids.isEmpty() ? List.of() : treatmentRepo.findByFindingIdIn(ids);
    }

    /** upsert 某发现的处置计划条目（发现须可见）。 */
    @Transactional
    public RiskTreatment upsertTreatment(Long findingId, String measure, String owner,
                                         java.time.LocalDate dueDate, String resource,
                                         RiskLevel expectedResidual, String status, String actor) {
        RiskFinding f = findingRepo.findById(findingId)
                .orElseThrow(() -> new IllegalArgumentException("风险发现不存在或不可见：id=" + findingId));
        RiskTreatment t = treatmentRepo.findByFindingId(findingId)
                .orElseGet(() -> new RiskTreatment(f.getOrgId(), findingId));
        t.apply(measure, owner, dueDate, resource, expectedResidual, status, actor);
        RiskTreatment saved = treatmentRepo.save(t);
        hashChainService.append(f.getOrgId(), "RTP_UPSERT", actor, "FINDING:" + findingId,
                "更新处置计划（RTP）：责任人=" + owner + " 期限=" + dueDate);
        return saved;
    }

    // ---------- RTP 汇总导出（.docx） ----------

    /** 评估级 RTP 汇总导出：发现×处置计划成表（ISO 27001 处置计划文档）。 */
    @Transactional(readOnly = true)
    public byte[] buildRtpDocx(Long assessmentId) {
        Assessment a = assessmentRepo.findById(assessmentId)
                .orElseThrow(() -> new IllegalArgumentException("评估不存在或不可见：id=" + assessmentId));
        List<RiskFinding> findings = findingRepo.findByAssessmentId(assessmentId);
        Map<Long, RiskTreatment> tmap = new java.util.HashMap<>();
        for (RiskTreatment t : listTreatments(assessmentId)) {
            tmap.put(t.getFindingId(), t);
        }

        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XWPFParagraph title = doc.createParagraph();
            title.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun tr = title.createRun();
            tr.setText("风险处置计划（RTP）· " + a.getTitle());
            tr.setBold(true);
            tr.setFontSize(16);
            doc.createParagraph().createRun().setText(
                    "评估编号：RA-" + a.getId() + "　范围：" + (a.getScope() == null ? "—" : a.getScope()));
            doc.createParagraph().createRun().setText(
                    "说明：本计划由平台按风险发现自动汇总；决策四选一见各发现，此处为落地要素（措施/责任人/期限/资源/预期残余）。");

            XWPFTable table = doc.createTable();
            XWPFTableRow head = table.getRow(0);
            String[] cols = {"发现", "固有", "决策", "处置措施", "责任人", "期限", "资源", "预期残余", "状态"};
            for (int i = 0; i < cols.length; i++) {
                (i == 0 ? head.getCell(0) : head.addNewTableCell()).setText(cols[i]);
            }
            for (RiskFinding f : findings) {
                RiskTreatment t = tmap.get(f.getId());
                XWPFTableRow row = table.createRow();
                String[] vals = {
                        "#" + f.getId() + " " + nvl(f.getTitle()),
                        f.getInherentLevel() == null ? "—" : LV_ZH.getOrDefault(f.getInherentLevel(), f.getInherentLevel().name()),
                        nvl(f.getTreatmentDecision()),
                        t == null ? nvl(f.getTreatmentPlan()) : nvl(t.getMeasure()),
                        t == null ? "—" : nvl(t.getOwner()),
                        t == null || t.getDueDate() == null ? "—" : t.getDueDate().toString(),
                        t == null ? "—" : nvl(t.getResource()),
                        t == null || t.getExpectedResidual() == null ? "—"
                                : LV_ZH.getOrDefault(t.getExpectedResidual(), t.getExpectedResidual().name()),
                        t == null ? "—" : t.getStatus()};
                for (int i = 0; i < vals.length && i < row.getTableCells().size(); i++) {
                    row.getCell(i).setText(vals[i]);
                }
            }
            doc.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException("RTP 导出失败", ex);
        }
    }

    private static String nvl(String s) {
        return s == null || s.isBlank() ? "—" : s;
    }

    static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
