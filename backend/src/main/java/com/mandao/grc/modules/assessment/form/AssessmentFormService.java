package com.mandao.grc.modules.assessment.form;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mandao.grc.modules.assessment.Assessment;
import com.mandao.grc.modules.assessment.AssessmentRepository;
import com.mandao.grc.modules.assessment.AssessmentTemplate;
import com.mandao.grc.modules.assessment.AssessmentTemplateRepository;
import com.mandao.grc.modules.assessment.RiskLevel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 风险评估表单引擎服务（P1）。
 *
 * 隔离：方法带 @Transactional 且位于 com.mandao.grc.modules 包，{@link com.mandao.grc.common.isolation.OrgScopeAspect}
 * 在事务内注入 app.visible_orgs，RLS 自动裁剪并校验写入。表单/填写均携 org_id（继承模板/评估的组织）。
 *
 * 能力：
 *  - 上传 .docx → 解析占位符 → 生成表单 schema → 存为模板的 DRAFT 表单版本；
 *  - 启用某表单版本（同模板仅一条 ACTIVE）；
 *  - 取评估的填写界面（首次访问按模板 ACTIVE 表单绑定快照）；
 *  - 保存填写值。
 */
@Service
public class AssessmentFormService {

    /** 留痕链（七轮 7-13 reprefill 留痕用，setter 注入）。 */
    private com.mandao.grc.modules.audit.HashChainService hashChainService;

    @org.springframework.beans.factory.annotation.Autowired
    void wireHashChain(com.mandao.grc.modules.audit.HashChainService hashChainService) {
        this.hashChainService = hashChainService;
    }

    private final DocxFormParser parser;
    private final TemplateFormRepository formRepo;
    private final AssessmentAnswerRepository answerRepo;
    private final AssessmentTemplateRepository templateRepo;
    private final AssessmentRepository assessmentRepo;
    private final ScoringService scoringService;
    private final DocxReportFiller reportFiller;
    private final ReportPdfService pdfService;
    private final ObjectMapper objectMapper;

    // UAT 五轮 #3 预填数据源（setter 注入，避免构造器继续膨胀）
    private com.mandao.grc.modules.assessment.AssessmentAssetRepository assessmentAssetRepo;
    private com.mandao.grc.modules.asset.AssetRepository assetRepo;
    private com.mandao.grc.modules.atv.RiskScenarioRepository scenarioRepo;
    private com.mandao.grc.modules.atv.ThreatRepository threatRepo;
    private com.mandao.grc.modules.atv.VulnerabilityRepository vulnRepo;

    @org.springframework.beans.factory.annotation.Autowired
    void wirePrefillSources(com.mandao.grc.modules.assessment.AssessmentAssetRepository assessmentAssetRepo,
                            com.mandao.grc.modules.asset.AssetRepository assetRepo,
                            com.mandao.grc.modules.atv.RiskScenarioRepository scenarioRepo,
                            com.mandao.grc.modules.atv.ThreatRepository threatRepo,
                            com.mandao.grc.modules.atv.VulnerabilityRepository vulnRepo) {
        this.assessmentAssetRepo = assessmentAssetRepo;
        this.assetRepo = assetRepo;
        this.scenarioRepo = scenarioRepo;
        this.threatRepo = threatRepo;
        this.vulnRepo = vulnRepo;
    }

    public AssessmentFormService(DocxFormParser parser, TemplateFormRepository formRepo,
                                 AssessmentAnswerRepository answerRepo,
                                 AssessmentTemplateRepository templateRepo,
                                 AssessmentRepository assessmentRepo,
                                 ScoringService scoringService, DocxReportFiller reportFiller,
                                 ReportPdfService pdfService, ObjectMapper objectMapper) {
        this.parser = parser;
        this.formRepo = formRepo;
        this.answerRepo = answerRepo;
        this.templateRepo = templateRepo;
        this.assessmentRepo = assessmentRepo;
        this.scoringService = scoringService;
        this.reportFiller = reportFiller;
        this.pdfService = pdfService;
        this.objectMapper = objectMapper;
    }

    // ---------- 模板侧：上传 / 列出 / 启用 ----------

    /**
     * 上传 .docx 并解析为模板的一个 DRAFT 表单版本。
     *
     * @param templateId 评估模板 id（须可见）
     * @param name       表单版本名（可空，缺省用模板名）
     * @param docxBytes  .docx 字节
     * @return 新建的表单版本（含解析 schema）
     */
    @Transactional
    public TemplateForm uploadForm(Long templateId, String name, byte[] docxBytes) {
        AssessmentTemplate tpl = templateRepo.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("评估模板不存在或不可见：id=" + templateId));
        FormSchema schema = parser.parse(docxBytes);
        String schemaJson = writeJson(schema);

        int nextVer = formRepo.findByTemplateIdOrderByVersionNoDesc(templateId).stream()
                .findFirst().map(f -> f.getVersionNo() + 1).orElse(1);
        String formName = (name == null || name.isBlank()) ? tpl.getName() + " 表单 v" + nextVer : name;

        TemplateForm form = new TemplateForm(tpl.getOrgId(), templateId, nextVer, formName, docxBytes, schemaJson);
        return formRepo.save(form);
    }

    /** 列出某模板的全部表单版本（版本号倒序）。 */
    @Transactional(readOnly = true)
    public List<TemplateForm> listForms(Long templateId) {
        return formRepo.findByTemplateIdOrderByVersionNoDesc(templateId);
    }

    /** 启用某表单版本：先停用同模板已有 ACTIVE，再置当前为 ACTIVE（保证唯一）。 */
    @Transactional
    public TemplateForm activate(Long formId) {
        TemplateForm form = formRepo.findById(formId)
                .orElseThrow(() -> new IllegalArgumentException("表单版本不存在或不可见：id=" + formId));
        formRepo.findFirstByTemplateIdAndStatus(form.getTemplateId(), TemplateForm.ACTIVE)
                .ifPresent(old -> {
                    if (!old.getId().equals(form.getId())) {
                        old.setStatus(TemplateForm.RETIRED);
                        formRepo.save(old);
                    }
                });
        form.setStatus(TemplateForm.ACTIVE);
        return formRepo.save(form);
    }

    /** 解析后的 schema（供上传响应/预览直接用，避免前端再反序列化）。 */
    public FormSchema schemaOf(TemplateForm form) {
        return readJson(form.getSchemaJson());
    }

    // ---------- 评估侧：取填写界面 / 保存 ----------

    /**
     * 取某评估的填写界面：schema + 当前 answers。
     *
     * 首次访问且评估关联了模板、模板有 ACTIVE 表单 → 绑定快照并建空填写；
     * 评估未关联模板或模板无 ACTIVE 表单 → 返回 hasForm=false（前端提示）。
     */
    @Transactional
    public AssessmentFormView getAssessmentForm(Long assessmentId) {
        Assessment a = assessmentRepo.findById(assessmentId)
                .orElseThrow(() -> new IllegalArgumentException("评估不存在或不可见：id=" + assessmentId));

        Optional<AssessmentAnswer> existing = answerRepo.findByAssessmentId(assessmentId);
        if (existing.isPresent()) {
            AssessmentAnswer ans = existing.get();
            TemplateForm form = formRepo.findById(ans.getFormVersionId()).orElse(null);
            if (form == null) {
                return AssessmentFormView.none("绑定的表单版本不可用");
            }
            return AssessmentFormView.of(form.getId(), readJson(form.getSchemaJson()), readAnswers(ans.getAnswersJson()));
        }

        if (a.getTemplateId() == null) {
            return AssessmentFormView.none("该评估未关联模板，无表单");
        }
        Optional<TemplateForm> active = formRepo.findFirstByTemplateIdAndStatus(a.getTemplateId(), TemplateForm.ACTIVE);
        if (active.isEmpty()) {
            return AssessmentFormView.none("该评估的模板尚未启用表单");
        }
        TemplateForm form = active.get();
        FormSchema schema = readJson(form.getSchemaJson());
        // UAT 五轮 #3（方向 A）：首次绑定时用系统结构化数据预填明细表——
        // 范围资产→资产清单、范围资产关联的 A-T-V 场景→威胁脆弱性清单/风险清单。
        // 填写=在系统数据快照上修订，报告与登记册天然一致；预填即落库（可再增删改）。
        Map<String, Object> prefill = buildPrefill(assessmentId, schema);
        AssessmentAnswer ans = new AssessmentAnswer(a.getOrgId(), assessmentId, form.getId(), writeJson(prefill));
        answerRepo.save(ans);
        return AssessmentFormView.of(form.getId(), schema, prefill);
    }

    /**
     * 从系统数据重新预填（七轮 7-13 / 评估报告 A28）：
     * 方向A预填只在首次绑定时执行，而真实操作顺序往往是「先打开详情（此刻绑死空快照）→再纳入范围资产」，
     * 三张明细表因此恒空。本方法按当前范围资产/ATV 场景重建三张系统明细表并覆盖回已绑定的答案
     * （仅覆盖 资产清单/威胁脆弱性清单/风险清单 三个键，用户手填的其它字段原样保留；
     * 覆盖是显式操作——前端按钮带确认提示）。
     */
    @Transactional
    public AssessmentFormView reprefill(Long assessmentId, String actor) {
        Assessment a = assessmentRepo.findById(assessmentId)
                .orElseThrow(() -> new IllegalArgumentException("评估不存在或不可见：id=" + assessmentId));
        AssessmentAnswer ans = answerRepo.findByAssessmentId(assessmentId)
                .orElseThrow(() -> new IllegalStateException("该评估尚未绑定表单——先打开评估表单再执行重新预填"));
        TemplateForm form = formRepo.findById(ans.getFormVersionId())
                .orElseThrow(() -> new IllegalStateException("绑定的表单版本不可用"));
        FormSchema schema = readJson(form.getSchemaJson());
        Map<String, Object> prefill = buildPrefill(assessmentId, schema);
        @SuppressWarnings("unchecked")
        Map<String, Object> current = (Map<String, Object>) readAnswers(ans.getAnswersJson());
        Map<String, Object> merged = current == null ? new java.util.HashMap<>() : new java.util.HashMap<>(current);
        merged.putAll(prefill); // 只有系统明细表键会被 buildPrefill 产出 → 只覆盖这三张表
        ans.setAnswersJson(writeJson(merged));
        answerRepo.save(ans);
        hashChainService.append(a.getOrgId(), "ASSESSMENT_REPREFILL", actor, "ASSESSMENT:" + assessmentId,
                "从系统数据重新预填明细表（范围资产/ATV 场景 → 三张清单）");
        return AssessmentFormView.of(form.getId(), schema, merged);
    }

    /**
     * 系统数据预填（仅当表单 schema 含对应明细表键时注入，自定义模板不受影响）：
     *  - 资产清单 ← assessment_asset（列：资产名称/资产类型/重要程度/资产说明）
     *  - 威胁脆弱性清单 ← 范围资产命中的 risk_scenario（列：威胁/脆弱性/已有措施）
     *  - 风险清单 ← 同上场景（列：风险描述/可能性/影响/固有等级/处置措施/残余等级）
     */
    private Map<String, Object> buildPrefill(Long assessmentId, FormSchema schema) {
        Map<String, Object> out = new java.util.HashMap<>();
        java.util.Set<String> listKeys = new java.util.HashSet<>();
        for (FormSchema.Section sec : schema.sections()) {
            for (FormSchema.ListBlock l : sec.lists()) {
                listKeys.add(l.key());
            }
        }
        if (listKeys.isEmpty()) {
            return out;
        }
        var scopeAssets = assessmentAssetRepo.findByAssessmentIdOrderByIdAsc(assessmentId);
        if (scopeAssets.isEmpty()) {
            return out;
        }
        // 资产清单
        java.util.List<Map<String, Object>> assetRows = new java.util.ArrayList<>();
        java.util.Map<Long, String> assetNames = new java.util.HashMap<>();
        for (var sa : scopeAssets) {
            var asset = assetRepo.findById(sa.getAssetId()).orElse(null);
            if (asset == null) {
                continue;
            }
            assetNames.put(asset.getId(), asset.getName());
            if (listKeys.contains("资产清单")) {
                Map<String, Object> row = new java.util.HashMap<>();
                row.put("资产名称", asset.getName());
                row.put("资产类型", asset.getAssetType() == null ? "" : asset.getAssetType());
                row.put("重要程度", mapCriticality(asset.getCriticality()));
                row.put("资产说明", "");
                assetRows.add(row);
            }
        }
        if (!assetRows.isEmpty()) {
            out.put("资产清单", assetRows);
        }
        // 范围资产命中的 A-T-V 场景 → 威胁脆弱性清单 / 风险清单
        java.util.List<Map<String, Object>> tvRows = new java.util.ArrayList<>();
        java.util.List<Map<String, Object>> riskRows = new java.util.ArrayList<>();
        for (Long assetId : assetNames.keySet()) {
            for (var sc : scenarioRepo.findByAssetId(assetId)) {
                String threat = threatRepo.findById(sc.getThreatId())
                        .map(com.mandao.grc.modules.atv.Threat::getName).orElse("威胁#" + sc.getThreatId());
                String vuln = vulnRepo.findById(sc.getVulnerabilityId())
                        .map(com.mandao.grc.modules.atv.Vulnerability::getName).orElse("脆弱性#" + sc.getVulnerabilityId());
                if (listKeys.contains("威胁脆弱性清单")) {
                    Map<String, Object> row = new java.util.HashMap<>();
                    row.put("威胁", threat);
                    row.put("脆弱性", vuln);
                    row.put("已有措施", sc.getDescription() == null ? "" : sc.getDescription());
                    tvRows.add(row);
                }
                if (listKeys.contains("风险清单")) {
                    Map<String, Object> row = new java.util.HashMap<>();
                    row.put("风险描述", assetNames.get(assetId) + "：" + threat + "（" + vuln + "）");
                    row.put("可能性", sc.getLikelihood());
                    row.put("影响", sc.getImpact());
                    row.put("固有等级", sc.getInherentLevel() == null ? "" : sc.getInherentLevel().name());
                    row.put("处置措施", "");
                    row.put("残余等级", "");
                    riskRows.add(row);
                }
            }
        }
        if (!tvRows.isEmpty()) {
            out.put("威胁脆弱性清单", tvRows);
        }
        if (!riskRows.isEmpty()) {
            out.put("风险清单", riskRows);
        }
        return out;
    }

    /** 资产重要程度（LOW/MEDIUM/HIGH/CRITICAL）→ 平台五级枚举名。 */
    private static String mapCriticality(String c) {
        if (c == null) {
            return "";
        }
        return switch (c) {
            case "CRITICAL" -> "VERY_HIGH";
            case "HIGH" -> "HIGH";
            case "MEDIUM" -> "MID";
            case "LOW" -> "LOW";
            default -> c;
        };
    }

    /**
     * 保存填写值（评估须已绑定表单；answers 为字段/列表键值 JSON）。
     *
     * 保存后按打分服务聚合整体残余等级 → 写回 assessment.riskLevel（驱动看板/任务列表风险等级
     * 与 CR-002 完成门控）。返回聚合出的整体等级（无可判定为 null）。
     */
    @Transactional
    public RiskLevel saveAnswers(Long assessmentId, Object answers) {
        AssessmentAnswer ans = answerRepo.findByAssessmentId(assessmentId)
                .orElseThrow(() -> new IllegalStateException("该评估尚未绑定表单，无法保存填写"));
        ans.setAnswersJson(writeJson(answers));
        answerRepo.save(ans);

        // 聚合整体残余等级并写回评估
        FormSchema schema = formRepo.findById(ans.getFormVersionId())
                .map(f -> readJson(f.getSchemaJson())).orElse(null);
        RiskLevel overall = scoringService.overallResidual(schema, asMap(answers));
        if (overall != null) {
            Assessment a = assessmentRepo.findById(assessmentId).orElse(null);
            if (a != null) {
                a.setRiskLevel(overall);
                assessmentRepo.save(a);
            }
        }
        return overall;
    }

    /** 把任意填写值规整为 Map（供打分聚合用）。 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object answers) {
        try {
            return objectMapper.convertValue(answers, new TypeReference<Map<String, Object>>() { });
        } catch (Exception e) {
            return Map.of();
        }
    }

    /** 取某表单版本（R4 模板中心 docx 下载用；不可见即视为不存在）。 */
    @Transactional(readOnly = true)
    public TemplateForm getForm(Long formId) {
        return formRepo.findById(formId)
                .orElseThrow(() -> new IllegalArgumentException("表单版本不存在或不可见：id=" + formId));
    }

    // ---------- 报告导出（P3）----------

    /**
     * 生成回填后的报告 docx：取评估绑定表单的 .docx 原件 + schema + 填写值 → 回填。
     *
     * @return 报告 docx 字节
     */
    @Transactional(readOnly = true)
    public byte[] buildReportDocx(Long assessmentId) {
        AssessmentAnswer ans = answerRepo.findByAssessmentId(assessmentId)
                .orElseThrow(() -> new IllegalStateException("该评估尚无填写或未绑定表单，无法导出报告"));
        TemplateForm form = formRepo.findById(ans.getFormVersionId())
                .orElseThrow(() -> new IllegalStateException("绑定的表单版本不可用"));
        if (form.getDocx() == null) {
            throw new IllegalStateException("该表单无 .docx 原件，无法回填导出");
        }
        FormSchema schema = readJson(form.getSchemaJson());
        Map<String, Object> answers = new java.util.HashMap<>(readAnswerMap(ans.getAnswersJson()));
        // V46：叠加"背景建立"保留占位符——模板里写 ${评估范围} 等即可回填评估元数据，
        // 无须在表单 schema 中定义（填写值同名时以填写值优先，不覆盖）。
        assessmentRepo.findById(assessmentId).ifPresent(a -> contextPlaceholders(a)
                .forEach((k, v) -> answers.putIfAbsent(k, v)));
        return reportFiller.fill(form.getDocx(), schema, answers);
    }

    /** 背景建立元数据 → 保留占位符键值（空值给"—"，报告不留裸占位符）。 */
    public static Map<String, Object> contextPlaceholders(Assessment a) {
        Map<String, Object> m = new java.util.HashMap<>();
        m.put("评估标题", nvl(a.getTitle()));
        m.put("评估范围", nvl(a.getScope()));
        m.put("评估目的", nvl(a.getObjective()));
        m.put("依据标准", nvl(a.getBasis()));
        m.put("评估方法", methodsLabel(a.getMethods()));
        m.put("评估准则", nvl(a.getCriteria()));
        m.put("评估组", nvl(a.getTeam()));
        m.put("评估人", nvl(a.getAssessor()));
        m.put("评估期间", a.getStartDate() == null && a.getEndDate() == null ? "—"
                : (a.getStartDate() == null ? "" : a.getStartDate().toString()) + " ~ "
                + (a.getEndDate() == null ? "" : a.getEndDate().toString()));
        return m;
    }

    /** 方式方法码 → 中文（逗号多值）。 */
    private static String methodsLabel(String methods) {
        if (methods == null || methods.isBlank()) {
            return "—";
        }
        Map<String, String> label = Map.of(
                "INTERVIEW", "人员访谈", "DOC_REVIEW", "文档核查", "TOOL_SCAN", "工具扫描",
                "PENTEST", "渗透测试", "CONFIG_CHECK", "配置核查");
        StringBuilder sb = new StringBuilder();
        for (String m : methods.split(",")) {
            if (!m.isBlank()) {
                sb.append(sb.length() > 0 ? "、" : "").append(label.getOrDefault(m.trim(), m.trim()));
            }
        }
        return sb.toString();
    }

    private static String nvl(String s) {
        return s == null || s.isBlank() ? "—" : s;
    }

    /** 生成回填后的报告 PDF（docx → LibreOffice 转 PDF）。 */
    @Transactional(readOnly = true)
    public byte[] buildReportPdf(Long assessmentId) {
        return pdfService.toPdf(buildReportDocx(assessmentId));
    }

    /** 解析填写 JSON 为 Map。 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> readAnswerMap(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() { });
        } catch (Exception e) {
            return Map.of();
        }
    }

    // ---------- JSON 辅助 ----------

    private String writeJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalArgumentException("序列化失败：" + e.getMessage(), e);
        }
    }

    private FormSchema readJson(String json) {
        try {
            return objectMapper.readValue(json, FormSchema.class);
        } catch (Exception e) {
            throw new IllegalStateException("表单 schema 损坏：" + e.getMessage(), e);
        }
    }

    private Object readAnswers(String json) {
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception e) {
            return java.util.Map.of();
        }
    }

    /** 评估填写界面视图。 */
    public record AssessmentFormView(boolean hasForm, String reason, Long formVersionId,
                                     FormSchema schema, Object answers) {
        static AssessmentFormView none(String reason) {
            return new AssessmentFormView(false, reason, null, null, null);
        }
        static AssessmentFormView of(Long formVersionId, FormSchema schema, Object answers) {
            return new AssessmentFormView(true, null, formVersionId, schema, answers);
        }
    }
}
