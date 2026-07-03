package com.mandao.grc.modules.assessment;

import com.mandao.grc.modules.audit.HashChainService;
import com.mandao.grc.modules.control.ControlFramework;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 评估模板业务服务（M2 评估模板库 + 实例化）。
 *
 * 隔离/留痕范式同其它模块：方法 @Transactional 且位于 com.mandao.grc.modules 包，切面注入 visible_orgs，
 * RLS 裁剪 + 写校验；关键操作经 {@link HashChainService#append} 入按 org 分链的哈希链。
 *
 * 核心：发布的模板可「实例化」为一次评估（{@link Assessment}）+ 若干评估项（{@link AssessmentItem}），
 * 评估项从模板项拷贝（含 control_id 引用），从而把"模板库 + 评估-控件复用"打通。
 * 模板状态机：DRAFT → PUBLISHED → RETIRED；仅 PUBLISHED 可实例化、仅 DRAFT 可改模板项。
 *
 * 设计依据：D1-2/D1-6（模板引擎、评估实例化）、D1-7（风险评估·模板库）、D2-5。
 */
@Service
public class TemplateService {

    private final AssessmentTemplateRepository templateRepository;
    private final AssessmentTemplateItemRepository templateItemRepository;
    private final AssessmentItemRepository assessmentItemRepository;
    private final AssessmentRepository assessmentRepository;
    private final HashChainService hashChainService;

    public TemplateService(AssessmentTemplateRepository templateRepository,
                           AssessmentTemplateItemRepository templateItemRepository,
                           AssessmentItemRepository assessmentItemRepository,
                           AssessmentRepository assessmentRepository,
                           HashChainService hashChainService) {
        this.templateRepository = templateRepository;
        this.templateItemRepository = templateItemRepository;
        this.assessmentItemRepository = assessmentItemRepository;
        this.assessmentRepository = assessmentRepository;
        this.hashChainService = hashChainService;
    }

    /** 列出当前可见组织范围内的全部模板。 */
    @Transactional(readOnly = true)
    public List<AssessmentTemplate> list() {
        return templateRepository.findAll();
    }

    /** 按 id 取模板（不可见则视为不存在）。 */
    @Transactional(readOnly = true)
    public AssessmentTemplate get(Long id) {
        return templateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("评估模板不存在或不可见：id=" + id));
    }

    /** 按序列出某模板的检查项。 */
    @Transactional(readOnly = true)
    public List<AssessmentTemplateItem> listItems(Long templateId) {
        get(templateId); // 可见性校验
        return templateItemRepository.findByTemplateIdOrderBySeqAsc(templateId);
    }

    /** 定义一个模板（DRAFT）。 */
    @Transactional
    public AssessmentTemplate create(Long orgId, String code, String name, ControlFramework framework,
                                     String description, String owner, String actor) {
        AssessmentTemplate t = new AssessmentTemplate(orgId, code, name, framework, description, owner);
        AssessmentTemplate saved = templateRepository.save(t);
        hashChainService.append(orgId, "TEMPLATE_CREATE", actor, "TEMPLATE:" + saved.getId(),
                "定义评估模板 code=" + code + " 框架=" + framework);
        return saved;
    }

    /**
     * 克隆模板（R4 模板中心：内置脚手架"克隆起步"的落地）：
     * 复制元数据与全部条款项到目标组织，新模板为 DRAFT（可增改后再发布）。
     */
    @Transactional
    public AssessmentTemplate clone(Long templateId, Long orgId, String code, String name, String actor) {
        AssessmentTemplate src = get(templateId);
        AssessmentTemplate copy = new AssessmentTemplate(orgId, code,
                name == null || name.isBlank() ? src.getName() + "（副本）" : name,
                src.getFramework(), src.getDescription(), actor);
        AssessmentTemplate saved = templateRepository.save(copy);
        for (AssessmentTemplateItem it : templateItemRepository.findByTemplateIdOrderBySeqAsc(templateId)) {
            templateItemRepository.save(new AssessmentTemplateItem(
                    orgId, saved.getId(), it.getSeq(), it.getControlId(), it.getClause(), it.getRequirement()));
        }
        hashChainService.append(orgId, "TEMPLATE_CLONE", actor, "TEMPLATE:" + saved.getId(),
                "克隆模板自 #" + templateId + "（" + src.getCode() + "）→ " + code);
        return saved;
    }

    /** 追加一条模板检查项（仅 DRAFT 模板可改），序号自动顺延。 */
    @Transactional
    public AssessmentTemplateItem addItem(Long templateId, Long controlId, String clause,
                                          String requirement, String actor) {
        AssessmentTemplate t = get(templateId);
        if (t.getStatus() != TemplateStatus.DRAFT) {
            throw new IllegalStateException("仅草稿(DRAFT)模板可增改检查项，当前状态：" + t.getStatus());
        }
        int seq = templateItemRepository.findByTemplateIdOrderBySeqAsc(templateId).size() + 1;
        AssessmentTemplateItem item = new AssessmentTemplateItem(
                t.getOrgId(), templateId, seq, controlId, clause, requirement);
        AssessmentTemplateItem saved = templateItemRepository.save(item);
        hashChainService.append(t.getOrgId(), "TEMPLATE_ADD_ITEM", actor, "TEMPLATE:" + templateId,
                "追加模板项 seq=" + seq + " clause=" + clause + (controlId == null ? "" : " control=" + controlId));
        return saved;
    }

    /** 发布模板：DRAFT → PUBLISHED（须至少 1 条检查项）。 */
    @Transactional
    public AssessmentTemplate publish(Long templateId, String actor) {
        AssessmentTemplate t = get(templateId);
        if (t.getStatus() != TemplateStatus.DRAFT) {
            throw new IllegalStateException("仅草稿(DRAFT)模板可发布，当前状态：" + t.getStatus());
        }
        if (templateItemRepository.findByTemplateIdOrderBySeqAsc(templateId).isEmpty()) {
            throw new IllegalStateException("模板无检查项，不可发布");
        }
        t.setStatus(TemplateStatus.PUBLISHED);
        AssessmentTemplate saved = templateRepository.save(t);
        hashChainService.append(t.getOrgId(), "TEMPLATE_PUBLISH", actor, "TEMPLATE:" + templateId, "发布模板");
        return saved;
    }

    /** 停用模板：DRAFT/PUBLISHED → RETIRED。 */
    @Transactional
    public AssessmentTemplate retire(Long templateId, String actor) {
        AssessmentTemplate t = get(templateId);
        if (t.getStatus() == TemplateStatus.RETIRED) {
            throw new IllegalStateException("模板已停用，无需重复停用");
        }
        t.setStatus(TemplateStatus.RETIRED);
        AssessmentTemplate saved = templateRepository.save(t);
        hashChainService.append(t.getOrgId(), "TEMPLATE_RETIRE", actor, "TEMPLATE:" + templateId, "停用模板");
        return saved;
    }

    /**
     * 实例化模板为一次评估（核心）：仅 PUBLISHED 模板可实例化。
     * 新建一个 {@link Assessment}（DRAFT），并把模板项逐条拷贝为 {@link AssessmentItem}（含 control_id 引用）。
     * 模板与评估同组织；全过程同事务原子 + 留痕。
     *
     * @return 新建的评估
     */
    @Transactional
    public Assessment instantiate(Long templateId, String title, String assessor, String period, String actor) {
        AssessmentTemplate t = get(templateId);
        if (t.getStatus() != TemplateStatus.PUBLISHED) {
            throw new IllegalStateException("仅已发布(PUBLISHED)模板可实例化，当前状态：" + t.getStatus());
        }
        List<AssessmentTemplateItem> templateItems = templateItemRepository.findByTemplateIdOrderBySeqAsc(templateId);

        // 1) 新建评估（DRAFT）
        Assessment assessment = new Assessment(t.getOrgId(), title, assessor, period);
        Assessment savedAssessment = assessmentRepository.save(assessment);

        // 2) 模板项 → 评估项逐条拷贝（含 control_id 复用）
        for (AssessmentTemplateItem ti : templateItems) {
            AssessmentItem ai = new AssessmentItem(t.getOrgId(), savedAssessment.getId(),
                    ti.getSeq(), ti.getControlId(), ti.getClause(), ti.getRequirement());
            assessmentItemRepository.save(ai);
        }

        hashChainService.append(t.getOrgId(), "TEMPLATE_INSTANTIATE", actor, "TEMPLATE:" + templateId,
                "实例化为评估 assessmentId=" + savedAssessment.getId() + " 项数=" + templateItems.size());
        return savedAssessment;
    }
}
