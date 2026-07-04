package com.mandao.grc.modules.regulation;

import com.mandao.grc.modules.audit.HashChainService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 法规模块 AI 链路的事务半区（七轮 7-7 / 评估报告 A5）。
 *
 * 背景：符合度评估/变更摘要曾整段 @Transactional——大模型外呼夹在事务中间，
 * 端点缓慢或挂死时数据库连接被一直占着（Hikari 默认 10 个），可拖垮整库。
 * 拆法：读半区（组装提示词所需数据）与写半区（回填结果+留痕）各自短事务，
 * LLM 外呼在两段事务之间裸跑（IsolationContext 是 ThreadLocal，跨事务仍在）。
 *
 * 注意：必须是独立 Bean——@Transactional 走代理，同类自调用不生效；
 * 且本类在 modules 包，OrgScopeAspect 照常注入 visible_orgs（RLS 语义不变）。
 */
@Service
public class RegulationAiTxSupport {

    private final RegulationRepository regulationRepository;
    private final RegulationChangeRepository changeRepository;
    private final RegulationPolicyMapRepository mapRepository;
    private final com.mandao.grc.modules.ai.AiGovernanceRepository governanceRepo;
    private final HashChainService hashChainService;

    /** 制度库（setter 注入与主服务同范式）。 */
    private com.mandao.grc.modules.policy.PolicyRepository policyRepository;

    @org.springframework.beans.factory.annotation.Autowired
    void wirePolicyRepository(com.mandao.grc.modules.policy.PolicyRepository policyRepository) {
        this.policyRepository = policyRepository;
    }

    public RegulationAiTxSupport(RegulationRepository regulationRepository,
                                 RegulationChangeRepository changeRepository,
                                 RegulationPolicyMapRepository mapRepository,
                                 com.mandao.grc.modules.ai.AiGovernanceRepository governanceRepo,
                                 HashChainService hashChainService) {
        this.regulationRepository = regulationRepository;
        this.changeRepository = changeRepository;
        this.mapRepository = mapRepository;
        this.governanceRepo = governanceRepo;
        this.hashChainService = hashChainService;
    }

    /** 符合度评估的提示词组装结果（读半区产物）。 */
    public record ComplianceInput(String question, List<String> snippets) {
    }

    /** 读半区：组装符合度评估提示词（校验映射/法规/制度可见与全文存在）。 */
    @Transactional(readOnly = true)
    public ComplianceInput loadComplianceInput(Long mapId) {
        RegulationPolicyMap m = mapRepository.findById(mapId)
                .orElseThrow(() -> new IllegalArgumentException("映射不存在或不可见：id=" + mapId));
        Regulation reg = regulationRepository.findById(m.getRegulationId())
                .orElseThrow(() -> new IllegalArgumentException("法规不存在或不可见：id=" + m.getRegulationId()));
        var policy = policyRepository.findById(m.getPolicyId())
                .orElseThrow(() -> new IllegalArgumentException("制度不存在或不可见：id=" + m.getPolicyId()));
        String content = policy.getContent();
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("该制度尚无全文（仅元数据），无法做符合度评估——请先在「制度发布」上传制度 .docx 原件");
        }
        String ask = promptTemplate(RegulationService.TPL_COMPLIANCE_ASSESS,
                "请以合规官视角，对比下述监管法规要求与企业制度全文，输出：结论（严格三选一：符合/部分符合/不符合）、"
                        + "差距说明、建议修订点（条款级，中文，简明）。");
        String latestChange = changeRepository.findByRegulationIdOrderByIdDesc(m.getRegulationId()).stream()
                .findFirst()
                .map(c -> "最新变更（" + c.getChangeType() + "）：" + c.getDescription())
                .orElse("");
        String body = content.length() > 6000 ? content.substring(0, 6000) + "\n…（全文过长已截断）" : content;
        String question = ask + "\n"
                + "【法规要求】" + reg.getTitle() + "（" + reg.getCode() + "，" + reg.getIssuer() + "）\n"
                + "适用条款：" + (m.getClause() == null ? "整体" : m.getClause()) + "\n"
                + "法规摘要：" + (reg.getSummary() == null ? "—" : reg.getSummary()) + "\n"
                + (latestChange.isEmpty() ? "" : latestChange + "\n")
                + "【制度全文】" + policy.getTitle() + "（v" + policy.getVersion() + "）\n" + body;
        return new ComplianceInput(question,
                List.of("法规：" + reg.getTitle(), "制度：" + policy.getTitle()));
    }

    /** 写半区：回填符合度评估结果并留痕。 */
    @Transactional
    public RegulationPolicyMap saveComplianceResult(Long mapId, String verdict, String answer, String actor) {
        RegulationPolicyMap m = mapRepository.findById(mapId)
                .orElseThrow(() -> new IllegalArgumentException("映射不存在或不可见：id=" + mapId));
        m.recordAssessment(verdict, answer);
        RegulationPolicyMap saved = mapRepository.save(m);
        hashChainService.append(m.getOrgId(), "REG_MAP_ASSESS", actor, "REGULATION:" + m.getRegulationId(),
                "AI 符合度评估 mapId=" + mapId + " 结论=" + saved.getAssessVerdict());
        return saved;
    }

    /** 变更摘要的提示词组装结果（读半区产物）。 */
    public record SummarizeInput(String question, List<String> snippets) {
    }

    /** 读半区：组装变更摘要提示词。 */
    @Transactional(readOnly = true)
    public SummarizeInput loadSummarizeInput(Long changeId) {
        RegulationChange c = changeRepository.findById(changeId)
                .orElseThrow(() -> new IllegalArgumentException("法规变更不存在或不可见：id=" + changeId));
        Regulation reg = regulationRepository.findById(c.getRegulationId())
                .orElseThrow(() -> new IllegalArgumentException("法规不存在或不可见：id=" + c.getRegulationId()));
        String ask = promptTemplate(RegulationService.TPL_CHANGE_SUMMARY,
                "请对以下法规变更做条款级要点摘要（合规影响导向，中文，3 条以内）：");
        String question = ask + "\n"
                + "法规：" + reg.getTitle() + "（" + reg.getCode() + "）\n"
                + "变更类型：" + c.getChangeType() + "，变更描述：" + c.getDescription();
        return new SummarizeInput(question, List.of(
                "法规标题：" + reg.getTitle(),
                "变更描述：" + c.getDescription()));
    }

    /** 写半区：回填变更 AI 摘要并留痕。 */
    @Transactional
    public RegulationChange saveSummary(Long changeId, String summary, String actor) {
        RegulationChange c = changeRepository.findById(changeId)
                .orElseThrow(() -> new IllegalArgumentException("法规变更不存在或不可见：id=" + changeId));
        c.setAiSummary(summary);
        RegulationChange saved = changeRepository.save(c);
        hashChainService.append(c.getOrgId(), "REG_AI_SUMMARY", actor, "REGULATION:" + c.getRegulationId(),
                "生成变更 AI 摘要 changeId=" + changeId);
        return saved;
    }

    /** 匹配建议的读半区：法规信息 + 制度清单（只取轻量字段，不碰 bytea）。 */
    @Transactional(readOnly = true)
    public RegulationService.MapSuggestion loadSuggestInputOrEmpty(Long regulationId) {
        Regulation reg = regulationRepository.findById(regulationId)
                .orElseThrow(() -> new IllegalArgumentException("法规不存在或不可见：id=" + regulationId));
        List<Object[]> policies = policyRepository.findIdTitleStatus();
        if (policies.isEmpty()) {
            return new RegulationService.MapSuggestion(regulationId,
                    "当前可见范围内暂无制度，无法给出匹配建议——请先在「制度发布」登记制度。", true, null);
        }
        StringBuilder plist = new StringBuilder();
        for (Object[] p : policies) {
            plist.append("- [").append(p[0]).append("] ").append(p[1]).append("（").append(p[2]).append("）\n");
        }
        String question = "以下法规需要建立与内部制度的映射关系。请从制度清单中找出可能受该法规约束、"
                + "应建立条款映射或需要修订的制度，逐条给出制度编号与理由（中文，仅从清单中选择，不得编造）。\n"
                + "法规：" + reg.getTitle() + "（" + reg.getCode() + "，" + reg.getIssuer() + "）\n"
                + "法规摘要：" + (reg.getSummary() == null ? "—" : reg.getSummary()) + "\n"
                + "【制度清单】\n" + plist;
        // suggestion 字段暂存 question，由主服务拿去外呼后替换为真正建议
        return new RegulationService.MapSuggestion(regulationId, question, true, "PENDING_LLM");
    }

    /** 提示词：治理里启用的同名模板优先，无则回退内置口径。 */
    private String promptTemplate(String name, String fallback) {
        return governanceRepo.findByKindAndEnabledTrue(
                        com.mandao.grc.modules.ai.AiGovernance.KIND_PROMPT_TEMPLATE).stream()
                .filter(g -> name.equals(g.getName()))
                .map(com.mandao.grc.modules.ai.AiGovernance::getDetail)
                .filter(d -> d != null && !d.isBlank())
                .findFirst()
                .orElse(fallback);
    }
}
