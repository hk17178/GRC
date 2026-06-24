package com.mandao.grc.modules.permission;

/**
 * SoD 职责分离红线违规异常（M8 红线）。
 *
 * 在授权（{@link PermissionService#grantRole}）时抛出：若被授权 user 在该 org 已持有与目标角色
 * 互斥（{@link SodRule}）的有效角色，且不存在针对该规则的有效 {@link SodException} 豁免，则拒绝授权。
 *
 * 单列为业务异常（而非泛化 IllegalStateException），便于上层精确识别 SoD 拦截、做差异化处置与告警。
 *
 * 设计依据：需求文档 M8 权限审批（SoD 红线）、D1-3 §4.7。
 */
public class SodViolationException extends RuntimeException {

    public SodViolationException(String message) {
        super(message);
    }
}
