package com.mandao.grc.common.auth;

/**
 * 身份再认证失败（安全加固包 A34）：签批等敏感操作的重输密码校验不通过。
 * 由全局异常处理器映射为 HTTP 401（此前误走 IllegalArgumentException→404，状态码语义错误）。
 */
public class ReauthFailedException extends RuntimeException {

    public ReauthFailedException(String message) {
        super(message);
    }
}
