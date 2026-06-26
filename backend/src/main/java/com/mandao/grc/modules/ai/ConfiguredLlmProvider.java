package com.mandao.grc.modules.ai;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * 运行期可配置大模型 Provider（@Primary）：按 DB 里的 web 配置动态选择并调用大模型。
 *
 * 优先级：
 *  - DB 配置 enabled 且 provider≠LOCAL 且已配密钥 → 用 DB 配置（CLAUDE / OPENAI 兼容）实时调用；
 *  - 否则回退到 env 装配的 Provider（保持向后兼容：未在界面配置时仍可用 env 方式；缺省即本地离线）。
 *
 * 如此既满足"运营在 web 配 API Key"，又不破坏既有 env 注入与离线默认。密钥仅在调用时从
 * {@link AiConfigService#snapshot()} 解密取用，不外泄、不日志。
 */
@Component
@Primary
public class ConfiguredLlmProvider implements LlmProvider {

    private final AiConfigService configService;
    private final ObjectProvider<List<LlmProvider>> allProviders;

    public ConfiguredLlmProvider(AiConfigService configService,
                                 ObjectProvider<List<LlmProvider>> allProviders) {
        this.configService = configService;
        this.allProviders = allProviders;
    }

    /**
     * env 装配的"非本类"Provider（向后兼容）：grc.ai.provider 条件装配的那一个——
     * 缺省为 LocalEchoLlmProvider（本地离线），设了 openai/claude 则为对应实现。二者互斥，恒有其一。
     */
    private LlmProvider envFallback() {
        List<LlmProvider> list = allProviders.getIfAvailable(List::of);
        return list.stream()
                .filter(p -> !(p instanceof ConfiguredLlmProvider))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("无可用 LlmProvider 兜底"));
    }

    private boolean dbActive(AiConfigService.Snapshot s) {
        return s.enabled()
                && !AiProviderConfig.LOCAL.equalsIgnoreCase(s.provider())
                && s.apiKey() != null && !s.apiKey().isBlank();
    }

    @Override
    public String name() {
        AiConfigService.Snapshot s = configService.snapshot();
        if (!dbActive(s)) {
            return envFallback().name();
        }
        return AiProviderConfig.CLAUDE.equalsIgnoreCase(s.provider()) ? "claude" : "openai-compatible";
    }

    @Override
    public String model() {
        AiConfigService.Snapshot s = configService.snapshot();
        if (!dbActive(s)) {
            return envFallback().model();
        }
        return s.model() == null ? name() : s.model();
    }

    @Override
    public String generate(String question, List<String> contextSnippets) {
        AiConfigService.Snapshot s = configService.snapshot();
        if (!dbActive(s)) {
            return envFallback().generate(question, contextSnippets);
        }
        try {
            if (AiProviderConfig.CLAUDE.equalsIgnoreCase(s.provider())) {
                return callClaude(s, question, contextSnippets);
            }
            return callOpenAi(s, question, contextSnippets);
        } catch (Exception e) {
            return "调用大模型失败：" + e.getMessage();
        }
    }

    // ---------- 提示词 ----------

    private String systemPrompt() {
        return "你是企业 GRC（治理·风险·合规）助手。只依据下方【知识库片段】回答，不得编造；"
                + "若片段不足以回答，请明确说明并建议补充相应制度/法规。回答末尾用 [序号] 标注引用来源。";
    }

    private String userPrompt(String question, List<String> snippets) {
        StringBuilder ctx = new StringBuilder();
        for (int i = 0; i < snippets.size(); i++) {
            ctx.append('[').append(i + 1).append("] ").append(snippets.get(i)).append('\n');
        }
        return "【知识库片段】\n" + (snippets.isEmpty() ? "（无召回片段）\n" : ctx) + "\n【问题】" + question;
    }

    // ---------- OpenAI 兼容 ----------

    private String callOpenAi(AiConfigService.Snapshot s, String question, List<String> snippets) {
        String base = s.baseUrl() == null || s.baseUrl().isBlank() ? "https://api.openai.com/v1" : s.baseUrl();
        RestClient http = RestClient.builder().baseUrl(base).build();
        Map<String, Object> body = Map.of(
                "model", s.model() == null ? "gpt-4o-mini" : s.model(),
                "max_tokens", s.maxTokens(),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt()),
                        Map.of("role", "user", "content", userPrompt(question, snippets))));
        OpenAiResponse resp = http.post().uri("/chat/completions")
                .header("Authorization", "Bearer " + s.apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(OpenAiResponse.class);
        if (resp != null && resp.choices() != null && !resp.choices().isEmpty()
                && resp.choices().get(0).message() != null) {
            return resp.choices().get(0).message().content();
        }
        return "（模型返回为空）";
    }

    // ---------- Anthropic Claude ----------

    private String callClaude(AiConfigService.Snapshot s, String question, List<String> snippets) {
        String base = s.baseUrl() == null || s.baseUrl().isBlank() ? "https://api.anthropic.com" : s.baseUrl();
        RestClient http = RestClient.builder().baseUrl(base).build();
        Map<String, Object> body = Map.of(
                "model", s.model() == null ? "claude-opus-4-8" : s.model(),
                "max_tokens", s.maxTokens(),
                "system", systemPrompt(),
                "messages", List.of(Map.of("role", "user", "content", userPrompt(question, snippets))));
        ClaudeResponse resp = http.post().uri("/v1/messages")
                .header("x-api-key", s.apiKey())
                .header("anthropic-version", "2023-06-01")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(ClaudeResponse.class);
        if (resp != null && resp.content() != null && !resp.content().isEmpty()) {
            return resp.content().get(0).text();
        }
        return "（模型返回为空）";
    }

    record OpenAiResponse(List<Choice> choices) {
        record Choice(Message message) {
        }

        record Message(String role, String content) {
        }
    }

    record ClaudeResponse(List<Block> content) {
        record Block(String type, String text) {
        }
    }
}
