package com.mandao.grc.modules.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mandao.grc.modules.dashboard.DashboardService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * AI 生成报送/汇报材料（需求 7.5.1）。
 *
 * 把当前可见组织的真实合规统计（DashboardService.summary，受 RLS 裁剪）作为上下文，
 * 交给 LlmProvider 生成 监管报送稿初稿 / 管理层合规简报。产出标注：
 *  - 数据时点（dataAsOf）；
 *  - "AI 生成初稿，须人工复核后使用"（needsReview 恒 true，前端强制展示提示）。
 * 本地离线 Provider 会返回检索式说明并诚实标注；接入通用大模型后为真生成式初稿。
 *
 * 提示词接治理（V42）：优先取 AI 治理里启用的同名提示词模板（「管理层简报」/「监管报送稿」），
 * 运营在「模型接入」页改模板正文即改生成口径；无启用模板时回退内置提示词。
 */
@Service
public class AiMaterialService {

    /** 治理提示词模板名 ↔ 材料类型的约定映射（与 V42 种子一致）。 */
    static final String TPL_MGMT_BRIEF = "管理层简报";
    static final String TPL_FILING_DRAFT = "监管报送稿";

    /** 内置回退提示词（治理里没有启用的同名模板时使用）。 */
    private static final String FALLBACK_MGMT_BRIEF =
            "请基于以下真实合规统计，为管理层撰写一份简明的合规态势简报（中文，分要点：总体态势/主要风险/整改与审计/建议，400 字内）。";
    private static final String FALLBACK_FILING_DRAFT =
            "请基于以下真实合规统计，起草一份对监管机构的定期报送材料初稿（中文，正式公文口吻，含 总体情况/风险与整改/合规义务履行 三节，500 字内）。";

    private final LlmProvider llm;
    private final DashboardService dashboardService;
    private final ObjectMapper objectMapper;
    private final AiGovernanceRepository governanceRepo;

    /** 留痕链（八轮 8-2：AI 生成访问日志，setter 注入）。 */
    private com.mandao.grc.modules.audit.HashChainService hashChainService;

    @org.springframework.beans.factory.annotation.Autowired
    void wireHashChain(com.mandao.grc.modules.audit.HashChainService hashChainService) {
        this.hashChainService = hashChainService;
    }

    /** AI 生成留痕（八轮 8-2）：类型与模型入链；失败不阻断生成。 */
    private void appendAccessLog(String action) {
        try {
            var orgs = com.mandao.grc.common.isolation.IsolationContext.get();
            long orgId = orgs == null || orgs.isEmpty() ? 1L : orgs.get(0);
            hashChainService.append(orgId, action, com.mandao.grc.common.auth.ActorResolver.resolve(null),
                    "AI:" + llm.name(), "生成材料初稿，模型=" + llm.model() + "（AI 初稿须人工复核）");
        } catch (RuntimeException e) {
            // 留痕失败不阻断生成
        }
    }

    public AiMaterialService(LlmProvider llm, DashboardService dashboardService, ObjectMapper objectMapper,
                             AiGovernanceRepository governanceRepo) {
        this.llm = llm;
        this.dashboardService = dashboardService;
        this.objectMapper = objectMapper;
        this.governanceRepo = governanceRepo;
    }

    /**
     * 生成材料初稿。
     *
     * @param type FILING_DRAFT 监管报送稿 / MGMT_BRIEF 管理层合规简报
     */
    public Material generate(String type) {
        // 八轮：本方法不再整段事务——统计聚合走 DashboardService 自己的事务，LLM 外呼裸跑（7-7 口径），留痕单独短事务
        String statsJson;
        try {
            statsJson = objectMapper.writeValueAsString(dashboardService.summary());
        } catch (Exception e) {
            statsJson = "{}";
        }
        boolean brief = "MGMT_BRIEF".equals(type);
        String ask = promptFor(brief ? TPL_MGMT_BRIEF : TPL_FILING_DRAFT,
                brief ? FALLBACK_MGMT_BRIEF : FALLBACK_FILING_DRAFT);
        String question = ask + "\n【统计数据(JSON)】" + statsJson;
        String draft = llm.generateFor("MATERIAL", question, List.of("当前合规统计：" + statsJson));
        appendAccessLog(brief ? "AI_GENERATE_BRIEF" : "AI_GENERATE_FILING");
        return new Material(type, draft, OffsetDateTime.now().toString(), true, llm.name());
    }

    /** 取治理里启用的同名提示词模板正文；无则回退内置。 */
    private String promptFor(String templateName, String fallback) {
        return governanceRepo.findByKindAndEnabledTrue(AiGovernance.KIND_PROMPT_TEMPLATE).stream()
                .filter(g -> templateName.equals(g.getName()))
                .map(AiGovernance::getDetail)
                .filter(d -> d != null && !d.isBlank())
                .findFirst()
                .orElse(fallback);
    }

    /** 生成结果：草稿/数据时点/须人工复核标记/提供方。 */
    public record Material(String type, String draft, String dataAsOf, boolean needsReview, String provider) {
    }
}
