package com.mandao.grc.modules.settings;

/**
 * 配置值类型（设置项的值约束）。更新时按类型校验取值合法性。
 */
public enum SettingValueType {
    /** 字符串（不校验）。 */
    STRING,
    /** 整数。 */
    INT,
    /** 布尔（true/false）。 */
    BOOL,
    /** JSON 文本。 */
    JSON
}
