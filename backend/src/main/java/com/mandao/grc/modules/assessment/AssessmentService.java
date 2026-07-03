package com.mandao.grc.modules.assessment;

import com.mandao.grc.modules.audit.HashChainService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 风险评估业务服务（M2 风险评估）。
 *
 * 隔离：本服务【不手动注入隔离上下文，也不手写 org 过滤】——只要方法带 @Transactional
 * 且位于 com.mandao.grc.modules 包，{@link com.mandao.grc.common.isolation.OrgScopeAspect}
 * 会在事务内自动注入 app.visible_orgs，随后 RLS 裁剪数据并校验写入（WITH CHECK）。
 *
 * 评估状态机：DRAFT → IN_PROGRESS → PENDING_REVIEW → COMPLETED；
 * PENDING_REVIEW 可退回 IN_PROGRESS。非法流转一律抛 {@link IllegalStateException}。
 *
 * 留痕：每次状态流转后调用 {@link HashChainService#append} 写入按 org 分链的防篡改哈希链。
 * HashChainService 与本服务同事务/同连接，共享 visible_orgs 注入，留痕与业务一致提交/回滚。
 *
 * 设计依据：D1-2（评估生命周期）、D1-3 §5.1/§8、D2-5 编码规范。
 */
@Service
public class AssessmentService {

    private final AssessmentRepository repository;
    private final HashChainService hashChainService;

    private com.mandao.grc.modules.asset.AssetService assetService;
    private AssessmentAssetRepository assessmentAssetRepository;

    @org.springframework.beans.factory.annotation.Autowired
    void wireScopeAssets(com.mandao.grc.modules.asset.AssetService assetService,
                         AssessmentAssetRepository assessmentAssetRepository) {
        this.assetService = assetService;
        this.assessmentAssetRepository = assessmentAssetRepository;
    }

    public AssessmentService(AssessmentRepository repository, HashChainService hashChainService) {
        this.repository = repository;
        this.hashChainService = hashChainService;
    }

    /** 列出当前主体可见组织范围内的评估（无 org 过滤，靠切面 + RLS）。 */
    @Transactional(readOnly = true)
    public List<Assessment> list() {
        return repository.findAll();
    }

    /** 按 id 取评估（仅能取到可见组织内的；不可见则视为不存在）。 */
    @Transactional(readOnly = true)
    public Assessment get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("评估不存在或不可见：id=" + id));
    }

    /**
     * 新建草稿评估。
     *
     * @param orgId 归属组织（必须在当前 visible_orgs 内，否则 RLS WITH CHECK 拒绝写入）
     * @param actor 操作人（用于留痕）
     */
    @Transactional
    public Assessment create(Long orgId, String title, String assessor, String period, String actor) {
        return create(orgId, title, assessor, period, null, actor);
    }

    /**
     * 新建草稿评估（可关联来源模板，供表单引擎渲染填写界面）。
     *
     * @param templateId 来源评估模板 id（可空）
     */
    @Transactional
    public Assessment create(Long orgId, String title, String assessor, String period,
                             Long templateId, String actor) {
        Assessment a = new Assessment(orgId, title, assessor, period, templateId);
        Assessment saved = repository.save(a);
        appendLog(saved, "ASSESSMENT_CREATE", actor, "新建评估草稿 title=" + title
                + (templateId == null ? "" : "（模板#" + templateId + "）"));
        return saved;
    }

    /** 开始评估：DRAFT → IN_PROGRESS。 */
    @Transactional
    public Assessment start(Long id, String actor) {
        Assessment a = get(id);
        transition(a, AssessmentStatus.DRAFT, AssessmentStatus.IN_PROGRESS);
        Assessment saved = repository.save(a);
        appendLog(saved, "ASSESSMENT_START", actor, "开始评估");
        return saved;
    }

    /** 提交复核：IN_PROGRESS → PENDING_REVIEW。 */
    @Transactional
    public Assessment submitForReview(Long id, String actor) {
        Assessment a = get(id);
        transition(a, AssessmentStatus.IN_PROGRESS, AssessmentStatus.PENDING_REVIEW);
        Assessment saved = repository.save(a);
        appendLog(saved, "ASSESSMENT_SUBMIT", actor, "提交复核");
        return saved;
    }

    /**
     * 背景建立（V46 · ISO 27005/GB/T 20984 ①阶段）：写入评估元数据。
     * 终态（COMPLETED）后不可再改——背景是报告的组成部分，定稿即冻结。
     */
    @Transactional
    public Assessment setContext(Long id, String scope, String objective, String basis, String methods,
                                 String criteria, String team,
                                 java.time.LocalDate startDate, java.time.LocalDate endDate, String actor) {
        Assessment a = get(id);
        if (a.getStatus() == AssessmentStatus.COMPLETED) {
            throw new IllegalStateException("评估已完成，背景信息随报告定稿冻结，不可修改");
        }
        a.applyContext(scope, objective, basis, methods, criteria, team, startDate, endDate);
        Assessment saved = repository.save(a);
        appendLog(saved, "ASSESSMENT_CONTEXT", actor, "背景建立/更新：范围·目的·依据·方法·准则·评估组·周期");
        return saved;
    }

    // ===== 评估范围资产（V48 · R2：资产识别清单）=====

    /** 范围资产视图（携资产名称/类型，前端免二次查询）。 */
    public record ScopeAssetView(Long id, Long assetId, String assetName, String assetType, String addedBy) {
    }

    @Transactional(readOnly = true)
    public java.util.List<ScopeAssetView> listScopeAssets(Long assessmentId) {
        get(assessmentId); // 可见性校验
        return assessmentAssetRepository.findByAssessmentIdOrderByIdAsc(assessmentId).stream()
                .map(a -> {
                    String name = "资产#" + a.getAssetId();
                    String type = null;
                    try {
                        var asset = assetService.get(a.getAssetId());
                        name = asset.getName();
                        type = asset.getAssetType();
                    } catch (RuntimeException ignore) {
                        // 资产已删除/不可见——保留 id 占位，不断链
                    }
                    return new ScopeAssetView(a.getId(), a.getAssetId(), name, type, a.getAddedBy());
                })
                .toList();
    }

    /** 勾选资产进评估范围（重复勾选幂等返回既有；资产须可见）。 */
    @Transactional
    public AssessmentAsset addScopeAsset(Long assessmentId, Long assetId, String actor) {
        Assessment a = get(assessmentId);
        if (a.getStatus() == AssessmentStatus.COMPLETED) {
            throw new IllegalStateException("评估已完成，范围资产随报告冻结");
        }
        var existing = assessmentAssetRepository.findByAssessmentIdAndAssetId(assessmentId, assetId);
        if (existing.isPresent()) {
            return existing.get();
        }
        assetService.get(assetId); // 可见性校验（不可见即视为不存在）
        AssessmentAsset saved = assessmentAssetRepository.save(
                new AssessmentAsset(a.getOrgId(), assessmentId, assetId, actor));
        appendLog(a, "ASSESSMENT_ASSET_ADD", actor, "范围纳入资产 asset=" + assetId);
        return saved;
    }

    /** 从评估范围移除资产。 */
    @Transactional
    public void removeScopeAsset(Long assessmentId, Long linkId, String actor) {
        Assessment a = get(assessmentId);
        if (a.getStatus() == AssessmentStatus.COMPLETED) {
            throw new IllegalStateException("评估已完成，范围资产随报告冻结");
        }
        assessmentAssetRepository.findById(linkId).ifPresent(link -> {
            assessmentAssetRepository.delete(link);
            appendLog(a, "ASSESSMENT_ASSET_REMOVE", actor, "范围移除资产 asset=" + link.getAssetId());
        });
    }

    /** 复核驳回：PENDING_REVIEW → IN_PROGRESS（退回继续评估）。 */
    @Transactional
    public Assessment reject(Long id, String actor, String reason) {
        Assessment a = get(id);
        transition(a, AssessmentStatus.PENDING_REVIEW, AssessmentStatus.IN_PROGRESS);
        Assessment saved = repository.save(a);
        appendLog(saved, "ASSESSMENT_REJECT", actor, "复核驳回，原因：" + (reason == null ? "" : reason));
        return saved;
    }

    /**
     * 完成评估：PENDING_REVIEW → COMPLETED（终态）。
     *
     * CR-002 红线门控：整体残余等级为高/极高（HIGH/VERY_HIGH）时，须先有管理层接受签批
     * （{@link Assessment#isMgmtAccepted()}），否则抛 {@link RiskCloseGateException}（→409）阻断完成。
     */
    @Transactional
    public Assessment complete(Long id, String actor) {
        Assessment a = get(id);
        RiskLevel lv = a.getRiskLevel();
        if ((lv == RiskLevel.HIGH || lv == RiskLevel.VERY_HIGH) && !a.isMgmtAccepted()) {
            throw new RiskCloseGateException(
                    "整体残余风险为" + (lv == RiskLevel.VERY_HIGH ? "极高" : "高") + "，须经管理层接受签批后方可完成评估");
        }
        transition(a, AssessmentStatus.PENDING_REVIEW, AssessmentStatus.COMPLETED);
        Assessment saved = repository.save(a);
        appendLog(saved, "ASSESSMENT_COMPLETE", actor, "评估完成");
        return saved;
    }

    /**
     * 管理层签批：记录签批人/意见/是否接受残余风险。
     *
     * accepted=true 即为接受残余风险，放行高/极高残余评估的完成门控（CR-002）。
     */
    @Transactional
    public Assessment signOff(Long id, String signer, String opinion, boolean accepted) {
        Assessment a = get(id);
        a.signOff(signer, opinion, accepted);
        Assessment saved = repository.save(a);
        appendLog(saved, "ASSESSMENT_SIGNOFF", signer,
                "管理层签批，" + (accepted ? "接受残余风险" : "不接受") + "：" + (opinion == null ? "" : opinion));
        return saved;
    }

    // ---------- 内部辅助 ----------

    /** 校验并执行一次合法流转：当前态须 == expectedFrom，否则视为非法流转抛异常。 */
    private void transition(Assessment a, AssessmentStatus expectedFrom, AssessmentStatus to) {
        if (a.getStatus() != expectedFrom) {
            throw new IllegalStateException(
                    "非法状态流转：评估 id=" + a.getId()
                            + " 当前状态=" + a.getStatus()
                            + "，仅允许从 " + expectedFrom + " 流转到 " + to);
        }
        a.setStatus(to);
    }

    /** 统一留痕入口：entity 统一格式 "ASSESSMENT:{id}"，便于审计按对象检索。 */
    private void appendLog(Assessment a, String action, String actor, String detail) {
        hashChainService.append(a.getOrgId(), action, actor, "ASSESSMENT:" + a.getId(), detail);
    }
}
