package com.mandao.grc.modules.ai;

/**
 * AI 场景（V49 模型分配的路由键）。每个场景可在「模型接入」页单独指定模型；
 * 未配置/停用则回退全局配置。
 */
public enum AiScenario {
    /** 智能问答（RAG）。 */
    QA,
    /** 报送/汇报材料生成。 */
    MATERIAL,
    /** 法规变更条款级摘要。 */
    REG_SUMMARY,
    /** 法规-制度匹配建议。 */
    POLICY_MAP
}
