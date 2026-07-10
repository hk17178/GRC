package com.mandao.grc;

import com.mandao.grc.common.auth.JwtService;
import com.mandao.grc.modules.ai.ConfigCrypto;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 安全加固单测（纯构造·无 Spring 上下文·无容器，秒级）。
 * 安全评审 H-9 / M-12：JWT 与配置主密钥缺失 / 为已知开发默认 / 过短 → 一律 fail-fast，拒绝以可预测密钥启动。
 */
class SecurityHardeningTest {

    @Test
    void jwt密钥_缺失或默认或过短_拒绝构造_足够强则通过() {
        assertThrows(IllegalStateException.class, () -> new JwtService("", 12), "空密钥应拒");
        assertThrows(IllegalStateException.class, () -> new JwtService("   ", 12), "空白密钥应拒");
        assertThrows(IllegalStateException.class,
                () -> new JwtService("dev-only-secret-change-me-please-32bytes!!", 12), "已知开发默认应拒");
        assertThrows(IllegalStateException.class, () -> new JwtService("short-key", 12), "过短(<32字节)应拒");
        assertDoesNotThrow(() -> new JwtService("this-is-a-strong-jwt-secret-with-more-than-32-bytes", 12),
                "足够长的强密钥应通过");
    }

    @Test
    void 配置主密钥_缺失或开发默认_拒绝构造_自定义则通过() {
        assertThrows(IllegalStateException.class, () -> new ConfigCrypto(""), "空主密钥应拒");
        assertThrows(IllegalStateException.class, () -> new ConfigCrypto("grc-dev-config-secret-change-me"),
                "开发默认主密钥应拒");
        assertDoesNotThrow(() -> new ConfigCrypto("a-strong-random-config-master-secret"),
                "自定义强主密钥应通过");
    }
}
