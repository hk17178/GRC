package com.mandao.grc.modules.asset;

/**
 * 资产分类分级（CR-002 资产合规属性之一），映射 asset.classification。
 *
 * 由低到高：PUBLIC（公开）→ INTERNAL（内部）→ SENSITIVE（敏感）。
 *
 * 设计依据：需求文档 M6 CR-002（分类分级）、D1-2、D2-5。
 */
public enum AssetClassification {

    /** 公开：可对外公开的信息资产。 */
    PUBLIC,

    /** 内部：仅限组织内部使用。 */
    INTERNAL,

    /** 敏感：最高敏感级别，受限范围访问。 */
    SENSITIVE
}
