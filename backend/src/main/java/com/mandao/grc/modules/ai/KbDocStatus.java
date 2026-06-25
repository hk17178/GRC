package com.mandao.grc.modules.ai;

/**
 * 知识源文档状态（AI 知识库 / RAG）。
 *
 * PENDING：已登记、尚未切块嵌入；INDEXED：已完成切块 + 向量嵌入，可被检索召回。
 */
public enum KbDocStatus {
    PENDING,
    INDEXED
}
