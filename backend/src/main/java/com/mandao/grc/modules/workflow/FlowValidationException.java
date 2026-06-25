package com.mandao.grc.modules.workflow;

/**
 * 审批流校验失败异常（画布结构/属性不合法）。
 *
 * 与"资源不存在(404)"、"状态冲突(409)"区分——校验失败是请求内容问题，映射 400，
 * 便于前端把"流程画错了/属性缺失"与系统错误区分并精确提示。message 聚合所有问题点。
 */
public class FlowValidationException extends RuntimeException {

    public FlowValidationException(String message) {
        super(message);
    }
}
