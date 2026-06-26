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

    private final DocxFormParser parser;
    private final TemplateFormRepository formRepo;
    private final AssessmentAnswerRepository answerRepo;
    private final AssessmentTemplateRepository templateRepo;
    private final AssessmentRepository assessmentRepo;
    private final ScoringService scoringService;
    private final ObjectMapper objectMapper;

    public AssessmentFormService(DocxFormParser parser, TemplateFormRepository formRepo,
                                 AssessmentAnswerRepository answerRepo,
                                 AssessmentTemplateRepository templateRepo,
                                 AssessmentRepository assessmentRepo,
                                 ScoringService scoringService, ObjectMapper objectMapper) {
        this.parser = parser;
        this.formRepo = formRepo;
        this.answerRepo = answerRepo;
        this.templateRepo = templateRepo;
        this.assessmentRepo = assessmentRepo;
        this.scoringService = scoringService;
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
        // 绑定快照：建一份空填写
        AssessmentAnswer ans = new AssessmentAnswer(a.getOrgId(), assessmentId, form.getId(), "{}");
        answerRepo.save(ans);
        return AssessmentFormView.of(form.getId(), readJson(form.getSchemaJson()), readAnswers("{}"));
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
