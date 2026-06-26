package com.mandao.grc.modules.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Component;

/**
 * 配置项对称加密（AES-256-CBC，spring-security-crypto）。
 *
 * 用于把界面录入的大模型 API Key 加密落库，避免明文存储。加密主密钥来自环境变量
 * {@code GRC_CONFIG_SECRET}（上线必配）；未配置时用开发默认值并告警。
 */
@Component
public class ConfigCrypto {

    private static final Logger log = LoggerFactory.getLogger(ConfigCrypto.class);
    private static final String DEV_DEFAULT = "grc-dev-config-secret-change-me";
    /** 固定盐（hex）。AES-CBC 用随机 IV 保证同明文不同密文，盐用于 KDF。 */
    private static final String SALT = "1a2b3c4d5e6f7081";

    private final TextEncryptor encryptor;

    public ConfigCrypto(@Value("${grc.config.secret:" + DEV_DEFAULT + "}") String secret) {
        if (DEV_DEFAULT.equals(secret)) {
            log.warn("GRC_CONFIG_SECRET 未配置，使用开发默认主密钥加密 AI 密钥——【上线必须配置强随机 GRC_CONFIG_SECRET】");
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
