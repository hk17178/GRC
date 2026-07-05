package com.mandao.grc.common.web;

import com.mandao.grc.modules.assessment.RiskCloseGateException;
import com.mandao.grc.modules.permission.SodViolationException;
import com.mandao.grc.modules.rbac.ForbiddenException;
import com.mandao.grc.modules.workflow.FlowValidationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器：把后端业务异常映射成"可区分的 HTTP 状态码 + 含 message 的清晰 JSON"。
 *
 * ===== 为什么需要它（红线端到端可见）=====
 * 业务异常（尤其 CR-002 残余风险关闭门控 {@link RiskCloseGateException}）若不显式映射，
 * Spring 默认一律按未捕获异常返回 HTTP 500、消息泛化。前端 client.js 解析错误时读
 * {@code data.message || data.error}，无法区分"被红线门控拦截"还是"普通系统错误"，
 * 红线在端到端就"看不见"了。本处理器为各类业务异常赋予语义化状态码与稳定的 code 字段，
 * 让前端可据 HTTP 状态码（409 vs 404）与 code（RISK_CLOSE_GATE 等）做差异化提示与处置。
 *
 * ===== 为什么把"门控/非法流转"从普通错误中单列 =====
 *  - 门控拦截（{@link RiskCloseGateException}）是【红线】：必须能被前端精确识别（code=RISK_CLOSE_GATE），
 *    与一般非法状态流转区分开，便于做红线告警/特殊文案，不被淹没在泛化错误里；
 *  - 非法状态流转（{@link IllegalStateException}）与门控同属"当前资源状态不允许该操作"，语义上都是 409 冲突，
 *    但 code 不同（ILLEGAL_STATE），方便定位是状态机拒绝而非门控拒绝；
 *  - 不存在/不可见（{@link IllegalArgumentException}，如 get() 在 RLS 裁剪后抛出）语义是"资源不存在"，
 *    映射 404，避免误报为系统错误，也避免泄露"存在但不可见"的信息。
 *
 * ===== 状态码与 code 映射表 =====
 *  - {@link RiskCloseGateException}  → 409 CONFLICT，  code = "RISK_CLOSE_GATE"（CR-002 关闭门控拦截，红线）
 *  - {@link IllegalStateException}   → 409 CONFLICT，  code = "ILLEGAL_STATE"  （非法状态流转，状态机拒绝）
 *  - {@link IllegalArgumentException}→ 404 NOT_FOUND， code = "NOT_FOUND"      （资源不存在或不可见）
 *
 * 风格参考 common 下既有子包（isolation/audit）：包级 Javadoc 说明设计意图，注释全中文。
 *
 * 设计依据：CR-002（残余风险关闭门控红线）、D2-5（前端功能以需求+设计+后端为准，红线端到端可见）。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 统一错误返回体。
     *
     * @param code    稳定的机器可读错误码（前端据此分支处置；不随文案变化）
     * @param message 面向人的清晰说明（来自异常 message；client.js 读取此字段展示）
     */
    public record ApiError(String code, String message) {
    }

    /**
     * CR-002 残余风险关闭门控拦截 → 409 CONFLICT。
     *
     * 单列为最高优先（红线）：前端凭 code=RISK_CLOSE_GATE 精确识别"被门控拦截"，
     * 而非泛化的 500/冲突，从而给出"需先登记风险接受方可关闭"的针对性提示。
     */
    @ExceptionHandler(RiskCloseGateException.class)
    public ResponseEntity<ApiError> handleRiskCloseGate(RiskCloseGateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("RISK_CLOSE_GATE", ex.getMessage()));
    }

    /**
     * 非法状态流转（状态机拒绝，如 AssessmentService/RiskFindingService 的 transition 校验失败）
     * → 409 CONFLICT。code=ILLEGAL_STATE，与门控区分。
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("ILLEGAL_STATE", ex.getMessage()));
    }

    /**
     * 资源不存在或不可见（如 get() 在 RLS 裁剪后 orElseThrow 抛 IllegalArgumentException）
     * → 404 NOT_FOUND。code=NOT_FOUND，不泄露"存在但不可见"。
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleNotFound(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("NOT_FOUND", ex.getMessage()));
    }

    /**
     * 身份再认证失败（安全加固包 A34：签批重输密码校验不通过）→ 401 UNAUTHORIZED。
     * 此前误借道 IllegalArgumentException 返回 404，状态码语义错误且易被误判为资源缺失。
     */
    @ExceptionHandler(com.mandao.grc.common.auth.ReauthFailedException.class)
    public ResponseEntity<ApiError> handleReauthFailed(com.mandao.grc.common.auth.ReauthFailedException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiError("REAUTH_FAILED", ex.getMessage()));
    }

    /**
     * M8 SoD 职责分离红线拦截（授权时命中互斥角色且无有效豁免）→ 409 CONFLICT。
     *
     * 与 CR-002 关闭门控同级——单列为可精确识别的红线（code=SOD_VIOLATION）：若不显式映射，
     * SodViolationException 作为未捕获 RuntimeException 会被 Spring 默认按 500 泛化返回，
     * 前端就无法把"职责分离冲突·需先审批豁免"与普通系统错误区分，红线在端到端不可见。
     */
    @ExceptionHandler(SodViolationException.class)
    public ResponseEntity<ApiError> handleSodViolation(SodViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("SOD_VIOLATION", ex.getMessage()));
    }

    /**
     * 数据完整性冲突（唯一约束/外键等，如同组织同年度重复登记年度计划命中 uk_*）→ 409 CONFLICT。
     *
     * 不回传底层 SQL/约束名（避免泄露库结构），给稳定可读的中文消息；code=DATA_CONFLICT。
     * 否则此类冲突会被默认按 500 返回，创建类操作（制度/计划/控件等唯一编码）UX 体验差。
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("DATA_CONFLICT", "数据冲突：已存在重复记录或违反唯一约束"));
    }

    /**
     * 审批流画布校验失败（结构/属性不合法）→ 400 BAD_REQUEST。
     *
     * 校验失败是"请求内容问题"（流程画错/属性缺失），与不存在(404)、状态冲突(409)区分；
     * code=FLOW_INVALID，message 聚合所有问题点，前端可直接展示给配置人员逐条修正。
     */
    @ExceptionHandler(FlowValidationException.class)
    public ResponseEntity<ApiError> handleFlowValidation(FlowValidationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError("FLOW_INVALID", ex.getMessage()));
    }

    /**
     * 功能级 RBAC 无权限拦截（已登录但无该操作权限）→ 403 FORBIDDEN，code=FORBIDDEN。
     * 与 401(未登录) 区分：前端据此提示"无操作权限"而非跳登录。
     */
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiError> handleForbidden(ForbiddenException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiError("FORBIDDEN", ex.getMessage()));
    }
}
