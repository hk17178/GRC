package com.mandao.grc.modules.ai;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 本地确定性嵌入（默认实现，无外网依赖）。
 *
 * 用「哈希特征袋 + L2 归一化」把文本映射为 1024 维向量：分词后将每个词哈希到一个维度桶并带符号累加，
 * 再做 L2 归一化。共享词越多的两段文本余弦相似度越高——足以驱动 RAG 召回链路端到端跑通，
 * 且完全确定性、不出网，保证 Testcontainers/CI 与离线环境可用。生产换 Voyage 等模型时本类被配置禁用。
 *
 * 分词：连续 ASCII 字母数字为一个词；非 ASCII 字母（中文等）按单字 + 相邻双字(bigram)，
 * 以在不引入分词器依赖的前提下捕获部分中文语序信息。
 *
 * matchIfMissing=true → 未配置 grc.ai.embedding.provider 时默认启用本地嵌入。
 */
@Component
@ConditionalOnProperty(name = "grc.ai.embedding.provider", havingValue = "local", matchIfMissing = true)
public class LocalDeterministicEmbeddingProvider implements EmbeddingProvider {

    private static final int DIM = 1024;

    @Override
    public int dim() {
        return DIM;
    }

    @Override
    public float[] embed(String text) {
        float[] v = new float[DIM];
        for (String tok : tokenize(text == null ? "" : text)) {
            int h = tok.hashCode();
            int bucket = Math.floorMod(h, DIM);
            int sign = ((tok + "#").hashCode() & 1) == 0 ? 1 : -1;
            v[bucket] += sign;
        }
        // L2 归一化（零向量保持全零，余弦比较时按 0 处理）
        double norm = 0;
        for (float x : v) {
            norm += (double) x * x;
        }
        if (norm > 0) {
            float inv = (float) (1.0 / Math.sqrt(norm));
            for (int i = 0; i < DIM; i++) {
                v[i] *= inv;
            }
        }
        return v;
    }

    /** 分词：ASCII 词 + 中文单字 + 中文相邻双字。全部转小写。 */
    private List<String> tokenize(String text) {
        String s = text.toLowerCase(Locale.ROOT);
        List<String> toks = new ArrayList<>();
        StringBuilder word = new StringBuilder();
        char prevCjk = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                word.append(c);
                prevCjk = 0;
            } else {
                if (word.length() > 0) {
                    toks.add(word.toString());
                    word.setLength(0);
                }
                if (Character.isLetter(c)) { // 非 ASCII 字母（中文等）
                    toks.add(String.valueOf(c));
                    if (prevCjk != 0) {
                        toks.add("" + prevCjk + c); // 双字
                    }
                    prevCjk = c;
                } else {
                    prevCjk = 0;
                }
            }
        }
        if (word.length() > 0) {
            toks.add(word.toString());
        }
        return toks;
    }
}
