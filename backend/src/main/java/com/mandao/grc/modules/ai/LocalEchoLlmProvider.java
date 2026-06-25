package com.mandao.grc.modules.ai;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 本地离线回答（默认实现，无外网、不编造）。
 *
 * 不接入大模型，只把向量召回的最相关片段做诚实摘要返回，并明确标注「本地离线模式·未接大模型」，
 * 引导配置 Claude 后获得完整生成式回答。如此既保证测试/离线可跑、不出网，也绝不伪造大模型回答
 * （合规底线：不展示后端没有的能力假象）。
 *
 * matchIfMissing=true → 未配置 grc.ai.provider 时默认本地离线模式。
 */
@Component
@ConditionalOnProperty(name = "grc.ai.provider", havingValue = "local", matchIfMissing = true)
public class LocalEchoLlmProvider implements LlmProvider {

    /** 片段摘录最大展示字符数。 */
    private static final int SNIPPET_MAX = 160;

    @Override
    public String name() {
        return "local";
    }

    @Override
    public String generate(String question, List<String> contextSnippets) {
        if (contextSnippets == null || contextSnippets.isEmpty()) {
            return "未在知识库检索到与问题相关的内容。可先在「知识库」录入相关制度/法规后再提问。";
        }
        String top = contextSnippets.get(0);
        if (top.length() > SNIPPET_MAX) {
            top = top.substring(0, SNIPPET_MAX) + "…";
        }
        return "【本地离线模式 · 未接入大模型】\n"
                + "已从知识库检索到 " + contextSnippets.size() + " 段相关内容（见下方引用）。最相关片段摘录：\n"
                + "「" + top + "」\n\n"
                + "配置 grc.ai.provider=claude 并注入 API 密钥后，将由 Claude 依据上述检索片段生成完整回答。";
    }
}
