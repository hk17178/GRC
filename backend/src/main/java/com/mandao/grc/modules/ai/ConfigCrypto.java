package com.mandao.grc.modules.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Component;

/**
 * 配置项对称加密（AES-256-CBC，spring-security-crypto）。
 *
 * 用于把界面录入的大模型 API Key 加密落库，避免明文存储。加密主密钥来自环境变量
 * {@code GRC_CONFIG_SECRET}（上线必配）；未配置或为开发默认值时启动即 fail-fast 拒绝运行（安全评审 M-12）。
 */
@Component
public class ConfigCrypto {

    private static final String DEV_DEFAULT = "grc-dev-config-secret-change-me";
    /** 固定盐（hex）。AES-CBC 用随机 IV 保证同明文不同密文，盐用于 KDF。 */
    private static final String SALT = "1a2b3c4d5e6f7081";

    private final TextEncryptor encryptor;

    public ConfigCrypto(@Value("${grc.config.secret:}") String secret) {
        // fail-fast（安全评审 M-12）：主密钥缺失/为开发默认一律拒绝启动，避免 AI 密钥以可预测密钥落库等同明文
        if (secret == null || secret.isBlank() || DEV_DEFAULT.equals(secret)) {
            throw new IllegalStateException(
                    "GRC_CONFIG_SECRET 未配置或为开发默认值——请设置强随机 GRC_CONFIG_SECRET 后再启动");
        }
        this.encryptor = Encryptors.text(secret, SALT);
    }

    public String encrypt(String plain) {
        return encryptor.encrypt(plain);
    }

    public String decrypt(String cipher) {
        return encryptor.decrypt(cipher);
    }

    /** 末 4 位掩码（仅用于界面展示"已配置"，不泄露完整密钥）。 */
    public String hint(String plain) {
        if (plain == null || plain.length() < 4) {
            return "****";
        }
        return "····" + plain.substring(plain.length() - 4);
    }
}
