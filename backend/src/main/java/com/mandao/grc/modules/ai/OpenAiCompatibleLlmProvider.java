package com.mandao.grc.modules.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * 通用 OpenAI-兼容回答提供方（解除单一厂商锁定）。
 *
 * 适配任何暴露 OpenAI 兼容 Chat Completions 接口的服务——只需配「base-url + api-key + model」：
 * OpenAI、通义千问(DashScope 兼容模式)、DeepSeek、Kimi、智谱 GLM、MiniMax，以及本地私有化
 * vLLM / Ollama / Xinference / LM Studio 等。本地私有化部署可满足等保/PIPL 数据不出境要求。
 *
 * 仅当 grc.ai.provider=openai 时装配（默认 local，本类不参与测试/离线）。<b>密钥仅经配置/环境变量注入，代码不含密钥</b>。
 * RAG 约束同 Claude 适配器：system 提示要求「只依据知识库片段作答、不编造、不足则明示、末尾标注引用」。
 *
 * base-url 约定指向到 /v1（如 https://api.openai.com/v1、http://本地:8000/v1），本类再 POST /chat/completions。
 */
@Component
@ConditionalOnProperty(name = "grc.ai.provider", havingValue = "openai")
public class OpenAiCompatibleLlmProvider implements LlmProvider {

    private final String apiKey;
    private final String modelName;
    private final int maxTokens;
    private final String baseUrl;
    private final AiEgressGuard egressGuard;

    public OpenAiCompatibleLlmProvider(
            @Value("${grc.ai.openai.api-key:}") String apiKey,
            @Value("${grc.ai.openai.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${grc.ai.openai.model:gpt-4o-mini}") String model,
            @Value("${grc.ai.openai.max-tokens:1024}") int maxTokens,
            AiEgressGuard egressGuard) {
        this.apiKey = apiKey;
        this.modelName = model;
        this.maxTokens = maxTokens;
        this.baseUrl = baseUrl;
        this.egressGuard = egressGuard; // 七轮 7-7/7-10：出站守卫统一供给带超时客户端并做白名单/SSRF 校验
    }

    @Override
    public String name() {
        return "openai-compatible";
    }

    @Override
    public String model() {
        return modelName;
    }

    @Override
    public String generate(String question, List<String> contextSnippets) {
        // 本地无鉴权服务（如 Ollama）可不配密钥；仅当确需鉴权而缺失时由远端返回 401，这里不强制拦截。
        String system = "你是企业 GRC（治理·风险·合规）助手。只依据下方【知识库片段】回答，不得编造；"
                + "若片段不足以回答，请明确说明并建议补充相应制度/法规。回答末尾用 [序号] 标注引用来源。";
        StringBuilder ctx = new StringBuilder();
        for (int i = 0; i < contextSnippets.size(); i++) {
            ctx.append('[').append(i + 1).append("] ").append(contextSnippets.get(i)).append('\n');
        }
        String user = "【知识库片段】\n" + (contextSnippets.isEmpty() ? "（无召回片段）\n" : ctx)
                + "\n【问题】" + question;

        Map<String, Object> body = Map.of(
                "model", modelName,
                "max_tokens", maxTokens,
                "messages", List.of(
                        Map.of("role", "system", "content", system),
                        Map.of("role", "user", "content", user)));
        try {
            RestClient http = egressGuard.clientFor(baseUrl); // 出站三层校验 + 连接/读超时
            OpenAiResponse resp = http.post()
                    .uri("/chat/completions")
                    .header("Authorization", apiKey == null || apiKey.isBlank() ? "" : "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(OpenAiResponse.class);
            if (resp != null && resp.choices() != null && !resp.choices().isEmpty()
                    && resp.choices().get(0).message() != null) {
                return resp.choices().get(0).message().content();
            }
            return "（模型返回为空）";
        } catch (Exception e) {
            return "调用大模型失败：" + e.getMessage();
        }
    }

    /** OpenAI 兼容 Chat Completions 响应（仅取所需字段）。 */
    record OpenAiResponse(List<Choice> choices) {
        record Choice(Message message) {
        }

        record Message(String role, String content) {
        }
    }
}
