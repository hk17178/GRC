package com.mandao.grc.modules.regulation;

import com.mandao.grc.modules.audit.HashChainService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 法规跟踪业务服务（法规库 + 变更动态 + 影响评估闭环）。
 *
 * 隔离/留痕范式同其它模块：方法 @Transactional 且位于 com.mandao.grc.modules 包，切面注入 visible_orgs，
 * RLS 裁剪 + 写校验；关键操作经 {@link HashChainService#append} 入按 org 分链的哈希链。
 *
 * 法规状态机 TRACKING → EFFECTIVE → SUPERSEDED/ABOLISHED；
 * 影响分析闭环：每条变更登记为 PENDING，须完成影响评估(记录受影响范围与处置)方置 ASSESSED。
 *
 * 设计依据：需求文档 M·法规跟踪（法规库/订阅/影响分析）、D2-5。
 */
@Service
public class RegulationService {

    /** 治理提示词模板名（V42）：运营建同名启用模板即可自定义摘要口径。 */
    static final String TPL_CHANGE_SUMMARY = "变更摘要";

    private final RegulationRepository regulationRepository;
    private final RegulationChangeRepository changeRepository;
    private final RegulationPolicyMapRepository mapRepository;
    private final com.mandao.grc.modules.ai.LlmProvider llmProvider;
    private final com.mandao.grc.modules.ai.AiGovernanceRepository governanceRepo;
    private final HashChainService hashChainService;

    /** 制度库（V49 匹配建议用；setter 注入避免构造器继续膨胀）。 */
    private com.mandao.grc.modules.policy.PolicyRepository policyRepository;

    @org.springframework.beans.factory.annotation.Autowired
    void wirePolicyRepository(com.mandao.grc.modules.policy.PolicyRepository policyRepository) {
        this.policyRepository = policyRepository;
    }

    public RegulationService(RegulationRepository regulationRepository,
                             RegulationChangeRepository changeRepository,
                             RegulationPolicyMapRepository mapRepository,
                             com.mandao.grc.modules.ai.LlmProvider llmProvider,
                             com.mandao.grc.modules.ai.AiGovernanceRepository governanceRepo,
                             HashChainService hashChainService) {
        this.regulationRepository = regulationRepository;
        this.changeRepository = changeRepository;
        this.mapRepository = mapRepository;
        this.llmProvider = llmProvider;
        this.governanceRepo = governanceRepo;
        this.hashChainService = hashChainService;
    }

    @Transactional(readOnly = true)
    public List<Regulation> list() {
        return regulationRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Regulation get(Long id) {
        return regulationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("法规不存在或不可见：id=" + id));
    }

    @Transactional(readOnly = true)
    public List<RegulationChange> listChanges(Long regulationId) {
        get(regulationId); // 可见性校验
        return changeRepository.findByRegulationIdOrderByIdDesc(regulationId);
    }

    /** 登记一条法规（TRACKING）。 */
    @Transactional
    public Regulation create(Long orgId, String code, String title, String issuer, String category,
                             LocalDate effectiveDate, String summary, String actor) {
        Regulation saved = regulationRepository.save(
                new Regulation(orgId, code, title, issuer, category, effectiveDate, summary));
        hashChainService.append(orgId, "REGULATION_CREATE", actor, "REGULATION:" + saved.getId(),
                "登记法规 code=" + code + " issuer=" + issuer);
        return saved;
    }

    /**
     * 更新法规状态（生效/被取代/废止）。仅允许从非终态推进；终态(SUPERSEDED/ABOLISHED)不可再变。
     */
    @Transactional
    public Regulation updateStatus(Long id, RegulationStatus target, String actor) {
        Regulation r = get(id);
        if (r.getStatus() == RegulationStatus.SUPERSEDED || r.getStatus() == RegulationStatus.ABOLISHED) {
            throw new IllegalStateException("法规已处于终态(" + r.getStatus() + ")，不可再变更状态");
        }
        if (target == RegulationStatus.TRACKING) {
            throw new IllegalStateException("不可回退到 TRACKING");
        }
        r.setStatus(target);
        Regulation saved = regulationRepository.save(r);
        hashChainService.append(r.getOrgId(), "REGULATION_STATUS", actor, "REGULATION:" + id,
                "法规状态 → " + target);
        return saved;
    }

    /** 登记一条法规变更动态（PENDING，待影响评估）。 */
    @Transactional
    public RegulationChange recordChange(Long regulationId, ChangeType type, LocalDate changeDate,
                                         String description, String actor) {
        Regulation r = get(regulationId);
        RegulationChange saved = changeRepository.save(
                new RegulationChange(r.getOrgId(), regulationId, type, changeDate, description));
        hashChainService.append(r.getOrgId(), "REGULATION_CHANGE", actor, "REGULATION:" + regulationId,
                "登记法规变更 type=" + type + " changeId=" + saved.getId());
        return saved;
    }

    /**
     * 完成法规变更的影响评估（影响分析闭环）：记录受影响范围与处置，PENDING → ASSESSED。
     * 重复评估（非 PENDING）一律拒绝。
     */
    @Transactional
    public RegulationChange assessImpact(Long changeId, String impactScope, String impactNote, String actor) {
        RegulationChange c = changeRepository.findById(changeId)
                .orElseThrow(() -> new IllegalArgumentException("法规变更不存在或不可见：id=" + changeId));
        if (c.getImpactStatus() != ImpactStatus.PENDING) {
            throw new IllegalStateException("该变更已完成影响评估，不可重复评估");
        }
        c.assess(impactScope, impactNote);
        RegulationChange saved = changeRepository.save(c);
        hashChainService.append(c.getOrgId(), "REGULATION_IMPACT_ASSESS", actor,
                "REGULATION:" + c.getRegulationId(),
                "完成影响评估 changeId=" + changeId + " 范围=" + impactScope);
        return saved;
    }

    // ---------- M4 深度：法规-制度映射 / AI 变更摘要 ----------

    /** 某法规命中的制度映射。 */
    @Transactional(readOnly = true)
    public List<RegulationPolicyMap> listMaps(Long regulationId) {
        return mapRepository.findByRegulationId(regulationId);
    }

    /** 登记法规-制度映射（法规条款 → 制度）。 */
    @Transactional
    public RegulationPolicyMap addMap(Long regulationId, Long policyId, String clause, String note, String actor) {
        Regulation reg = get(regulationId);
        RegulationPolicyMap saved = mapRepository.save(
                new RegulationPolicyMap(reg.getOrgId(), regulationId, policyId, clause, note));
        hashChainService.append(reg.getOrgId(), "REG_MAP_ADD", actor, "REGULATION:" + regulationId,
                "登记法规-制度映射 policy=" + policyId + " 条款=" + clause);
        return saved;
    }

    /** AI 匹配建议结果（V49 · POLICY_MAP 场景）：初稿须人工确认后再手工登记映射。 */
    public record MapSuggestion(Long regulationId, String suggestion, boolean needsReview, String provider) {
    }

    /**
     * AI 法规-制度匹配建议（V49 · POLICY_MAP 场景消费方）：
     * 把 法规信息+现有制度清单 交给 LlmProvider，产出"哪些制度可能受该法规约束/需建映射"的建议初稿。
     * 只出建议不落库——运营确认后再手工「登记映射」（AI 初稿须人工复核红线）。
     */
    @Transactional(readOnly = true)
    public MapSuggestion suggestPolicyMap(Long regulationId) {
        Regulation reg = get(regulationId);
        List<com.mandao.grc.modules.policy.Policy> policies = policyRepository.findAll();
        if (policies.isEmpty()) {
            return new MapSuggestion(regulationId, "当前可见范围内暂无制度，无法给出匹配建议——请先在「制度发布」登记制度。",
                    true, llmProvider.name());
        }
        StringBuilder plist = new StringBuilder();
        for (var p : policies) {
            plist.append("- [").append(p.getId()).append("] ").append(p.getTitle())
                    .append("（").append(p.getStatus()).append("）\n");
        }
        String question = "以下法规需要建立与内部制度的映射关系。请从制度清单中找出可能受该法规约束、"
                + "应建立条款映射或需要修订的制度，逐条给出制度编号与理由（中文，仅从清单中选择，不得编造）。\n"
                + "法规：" + reg.getTitle() + "（" + reg.getCode() + "，" + reg.getIssuer() + "）\n"
                + "法规摘要：" + (reg.getSummary() == null ? "—" : reg.getSummary()) + "\n"
                + "【制度清单】\n" + plist;
        String suggestion = llmProvider.generateFor("POLICY_MAP", question,
                List.of("制度清单：\n" + plist));
        return new MapSuggestion(regulationId, suggestion, true, llmProvider.name());
    }

    /**
     * 生成变更的 AI 条款级摘要（需求 6.5.1）：把 变更描述+法规上下文 交给 LlmProvider，
     * 结果落库到 regulation_change.ai_summary。本地离线 Provider 会返回检索式说明并诚实标注。
     */
    @Transactional
    public RegulationChange aiSummarize(Long changeId, String actor) {
        RegulationChange c = changeRepository.findById(changeId)
                .orElseThrow(() -> new IllegalArgumentException("法规变更不存在或不可见：id=" + changeId));
        Regulation reg = get(c.getRegulationId());
        // 提示词优先取治理里启用的「变更摘要」模板（V42），无则回退内置口径
        String ask = governanceRepo.findByKindAndEnabledTrue(
                        com.mandao.grc.modules.ai.AiGovernance.KIND_PROMPT_TEMPLATE).stream()
                .filter(g -> TPL_CHANGE_SUMMARY.equals(g.getName()))
                .map(com.mandao.grc.modules.ai.AiGovernance::getDetail)
                .filter(d -> d != null && !d.isBlank())
                .findFirst()
                .orElse("请对以下法规变更做条款级要点摘要（合规影响导向，中文，3 条以内）：");
        String question = ask + "\n"
                + "法规：" + reg.getTitle() + "（" + reg.getCode() + "）\n"
                + "变更类型：" + c.getChangeType() + "，变更描述：" + c.getDescription();
        String summary = llmProvider.generateFor("REG_SUMMARY", question, List.of(
                "法规标题：" + reg.getTitle(),
                "变更描述：" + c.getDescription()));
        c.setAiSummary(summary);
        RegulationChange saved = changeRepository.save(c);
        hashChainService.append(reg.getOrgId(), "REG_AI_SUMMARY", actor, "REGULATION:" + reg.getId(),
                "生成变更 AI 摘要 changeId=" + changeId);
        return saved;
    }
}
