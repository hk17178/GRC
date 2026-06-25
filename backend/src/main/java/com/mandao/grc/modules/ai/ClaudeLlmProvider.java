package com.mandao.grc.modules.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Claude（Anthropic Messages API）回答提供方。
 *
 * 仅当 grc.ai.provider=claude 时装配（默认 local，本类不参与测试/离线）。密钥经配置注入
 * （grc.ai.claude.api-key ← 环境变量 ANTHROPIC_API_KEY），<b>代码不含任何密钥</b>。
 *
 * RAG 约束：system 提示要求「只依据知识库片段作答、不得编造、不足则明示、末尾标注引用」，
 * 与合规底线一致（不输出无据内容）。密钥缺失或调用异常时返回可读的中文说明，不抛栈给前端。
 */
@Component
@ConditionalOnProperty(name = "grc.ai.provider", havingValue = "claude")
public class ClaudeLlmProvider implements LlmProvider {

    private final String apiKey;
    private final String model;
    private final int maxTokens;
    private final RestClient http;

    public ClaudeLlmProvider(
            @Value("${grc.ai.claude.api-key:}") String apiKey,
            @Value("${grc.ai.claude.model:claude-opus-4-8}") String model,
            @Value("${grc.ai.claude.base-url:https://api.anthropic.com}") String baseUrl,
            @Value("${grc.ai.claude.max-tokens:1024}") int maxTokens) {
        this.apiKey = apiKey;
        this.model = model;
        this.maxTokens = maxTokens;
        this.http = RestClient.builder().baseUrl(baseUrl).build();
    }

    @Override
    public String name() {
        return "claude";
    }

    @Override
    public String model() {
        return model;
    }

    @Override
    public String generate(String question, List<String> contextSnippets) {
        if (apiKey == null || apiKey.isBlank()) {
            return "AI 提供方已配置为 Claude，但未注入 API 密钥（grc.ai.claude.api-key / 环境变量 ANTHROPIC_API_KEY）。"
                    + "请在部署环境配置密钥后重试。";
        }
        String system = "你是企业 GRC（治理·风险·合规）助手。只依据下方【知识库片段】回答，不得编造；"
                + "若片段不足以回答，请明确说明并建议补充相应制度/法规。回答末尾用 [序号] 标注引用来源。";
        StringBuilder ctx = new StringBuilder();
        for (int i = 0; i < contextSnippets.size(); i++) {
            ctx.append('[').append(i + 1).append("] ").append(contextSnippets.get(i)).append('\n');
        }
        String user = "【知识库片段】\n" + (contextSnippets.isEmpty() ? "（无召回片段）\n" : ctx)
                + "\n【问题】" + question;

        Map<String, Object> body = Map.of(
                "model", model,
                "max_tokens", maxTokens,
                "system", system,
                "messages", List.of(Map.of("role", "user", "content", user)));
        try {
            ClaudeResponse resp = http.post()
                    .uri("/v1/messages")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(ClaudeResponse.class);
            if (resp != null && resp.content() != null && !resp.content().isEmpty()) {
                return resp.content().get(0).text();
            }
            return "（Claude 返回为空）";
        } catch (Exception e) {
            return "调用 Claude 失败：" + e.getMessage();
        }
    }

    /** Anthropic Messages 响应（仅取所需字段）。 */
    record ClaudeResponse(List<Content> content) {
        record Content(String type, String text) {
        }
    }
}
