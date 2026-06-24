package com.mandao.grc.modules.audit.management;

/**
 * 外部审计对外回函三段漏斗非法流转异常（M3 红线）。
 *
 * 在以下任一情形抛出，阻断漏斗推进：
 *   1) 非外审发现（audit_type != EXTERNAL）试图走对外回函漏斗；
 *   2) 跳级（如 SUBMITTED 直接到 CONFIRMED_CLOSED）；
 *   3) 逆向 / 原地重复（如 ACCEPTED 退回 SUBMITTED，或重复提交同一段）。
 *
 * 单列为业务异常（而非泛化 IllegalStateException），便于上层精确识别漏斗拦截、做差异化处置与告警。
 *
 * 设计依据：需求文档 M3 审计管理（外审三段漏斗红线）。
 */
public class AuditFunnelException extends RuntimeException {

    public AuditFunnelException(String message) {
        super(message);
    }
}
