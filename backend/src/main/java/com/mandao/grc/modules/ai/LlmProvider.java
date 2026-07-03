package com.mandao.grc.modules.ai;

import java.util.List;

/**
 * 大模型回答提供方抽象（RAG 生成端，可插拔）。
 *
 * 默认实现 {@link LocalEchoLlmProvider}（本地离线、无外网、不编造：仅回检索摘要并诚实标注未接大模型）；
 * 生产经配置 grc.ai.provider=claude 切 {@link ClaudeLlmProvider}（密钥由部署注入，代码不含密钥）。
 *
 * 约定：实现须「仅依据传入的检索片段作答、不得编造」；片段不足以回答时应明确说明。
 */
public interface LlmProvider {

    /** 提供方标识（用于前端展示当前 AI 模式，如 local / claude / openai-compatible）。 */
    String name();

    /** 当前模型标识（用于前端展示具体模型名）。默认与提供方标识相同。 */
    default String model() {
        return name();
    }

    /**
     * 依据问题与知识库检索片段生成回答。
     *
     * @param question        用户问题
     * @param contextSnippets 召回的知识片段（按相关度降序）
     * @return 回答文本
     */
    String generate(String question, List<String> contextSnippets);

    /**
     * 按场景生成（V49 模型分配）：支持路由的实现（ConfiguredLlmProvider）按场景选模型；
     * 其余实现默认忽略场景直接生成——调用方统一带场景，无须关心提供方是否支持路由。
     *
     * @param scenario AI 场景（{@link AiScenario} 枚举名；null=全局默认）
     */
    default String generateFor(String scenario, String question, List<String> contextSnippets) {
        return generate(question, contextSnippets);
    }
}
