package com.mandao.grc.modules.ai;

/**
 * 知识源文档类型（AI 知识库 / RAG）。
 *
 * 标识 {@link KbDocument} 的来源，便于按类型组织检索与展示。
 */
public enum KbSourceType {
    /** 制度文档（M1）。 */
    POLICY,
    /** 法规文档（M11 法规跟踪）。 */
    REGULATION,
    /** 合规义务（合规清单）。 */
    OBLIGATION,
    /** 手工录入的其它知识。 */
    MANUAL
}
