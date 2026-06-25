package com.mandao.grc.modules.ai;

/**
 * 文本嵌入提供方抽象（RAG 可插拔）。
 *
 * 默认实现 {@link LocalDeterministicEmbeddingProvider}（本地确定性、无外网，保障测试/离线可跑）；
 * 生产可切换为外部嵌入服务（如 Voyage）——通过配置 grc.ai.embedding.provider 选择，密钥由部署注入。
 * 所有实现须产出相同维度（与 kb_chunk.embedding 列 vector(1024) 一致）。
 */
public interface EmbeddingProvider {

    /** 嵌入维度（须与向量列定义一致）。 */
    int dim();

    /** 将文本映射为定长向量。 */
    float[] embed(String text);
}
