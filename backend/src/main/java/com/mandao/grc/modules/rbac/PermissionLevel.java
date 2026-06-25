package com.mandao.grc.modules.rbac;

/** 权限级别：读写 / 只读 / 隐藏。未授权资源默认视为 HIDDEN（默认拒绝）。 */
public enum PermissionLevel {
    RW, RO, HIDDEN
}
