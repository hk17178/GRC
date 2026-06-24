package com.mandao.grc.modules.assessment;

/**
 * 评估模板状态机：DRAFT → PUBLISHED → RETIRED。
 * 仅 PUBLISHED 模板可实例化为评估；DRAFT 可编辑（增删模板项）；RETIRED 停用。
 */
public enum TemplateStatus {
    /** 草稿：可编辑模板项，未发布不可实例化。 */
    DRAFT,
    /** 已发布：可被实例化为评估，不可再改模板项。 */
    PUBLISHED,
    /** 停用：终态，不再实例化。 */
    RETIRED
}
