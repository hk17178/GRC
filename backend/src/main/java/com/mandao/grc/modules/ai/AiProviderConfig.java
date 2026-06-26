package com.mandao.grc.modules.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * 大模型接入配置（平台级单行，id 固定为 1）。
 *
 * 运营在界面配置 provider/base_url/model + API Key；api_key 加密存储（{@link ConfigCrypto}），
 * 不回显明文。运行期由 {@link ConfiguredLlmProvider} 据此动态选择并调用大模型。
 */
@Entity
@Table(name = "ai_provider_config")
public class AiProviderConfig {

    public static final String LOCAL = "LOCAL";
    public static final String CLAUDE = "CLAUDE";
    public static final String OPENAI = "OPENAI";

    @Id
    private Long id = 1L;

    @Column(nullable = false, length = 16)
    private String provider = LOCAL;

    @Column(name = "base_url", columnDefinition = "TEXT")
    private String baseUrl;

    @Column(length = 128)
    private String model;

    @Column(name = "max_tokens", nullable = false)
    private int maxTokens = 1024;

    /** AES 加密后的密钥（非明文）。 */
    @Column(name = "api_key_enc", columnDefinition = "TEXT")
    private String apiKeyEnc;

    /** 掩码提示（末 4 位）。 */
    @Column(name = "key_hint", length = 16)
    private String keyHint;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "updated_by", length = 64)
    private String updatedBy;

    protected AiProviderConfig() {
    }

    @PreUpdate
    void touch() {
        this.updatedAt = OffsetDateTime.now();
    }

    public void apply(String provider, String baseUrl, String model, int maxTokens, boolean enabled, String actor) {
        this.provider = provider == null ? LOCAL : provider;
        this.baseUrl = baseUrl;
        this.model = model;
        this.maxTokens = maxTokens <= 0 ? 1024 : maxTokens;
        this.enabled = enabled;
        this.updatedBy = actor;
        this.updatedAt = OffsetDateTime.now();
    }

    /** 设置加密密钥 + 末位掩码（plain 为空表示不变，由 Service 控制是否调用）。 */
    public void setKey(String encrypted, String hint) {
        this.apiKeyEnc = encrypted;
        this.keyHint = hint;
    }

    public Long getId() { return id; }
    public String getProvider() { return provider; }
    public String getBaseUrl() { return baseUrl; }
    public String getModel() { return model; }
    public int getMaxTokens() { return maxTokens; }
    public String getApiKeyEnc() { return apiKeyEnc; }
    public String getKeyHint() { return keyHint; }
    public boolean isEnabled() { return enabled; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public String getUpdatedBy() { return updatedBy; }
}
