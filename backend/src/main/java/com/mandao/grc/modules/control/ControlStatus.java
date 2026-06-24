package com.mandao.grc.modules.control;

/**
 * 控制项状态。ACTIVE 可被评估引用与新增映射；RETIRED 停用（保留历史，不再新引用）。
 */
public enum ControlStatus {
    /** 启用：可引用、可新增框架映射。 */
    ACTIVE,
    /** 停用：终态，保留历史不再新引用。 */
    RETIRED
}
