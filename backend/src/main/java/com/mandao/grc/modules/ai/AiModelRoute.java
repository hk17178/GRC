package com.mandao.grc.modules.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * 场景模型路由（V49，平台级无 RLS）：一个 AI 场景一行，指定该场景用哪套 provider/model/key。
 *
 * 场景：QA / MATERIAL / REG_SUMMARY / POLICY_MAP（见 {@link AiScenario}）。
 * enabled=false 或未配密钥（非 LOCAL）→ 该场景回退全局 ai_provider_config。
 */
@Entity
@Table(name = "ai_model_route")
public class AiModelRoute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 24, unique = true)
    private String scenario;

    @Column(nullable = false, length = 16)
    private String provider = AiProviderConfig.LOCAL;

    @Column(name = "base_url", columnDefinition = "TEXT")
    private String baseUrl;

    @Column(length = 128)
    private String model;

    @Column(name = "max_tokens", nullable = false)
    private int maxTokens = 1024;

    @Column(name = "api_key_enc", columnDefinition = "TEXT")
    private String apiKeyEnc;

    @Column(name = "key_hint", length = 16)
    private String keyHint;

    @Column(nullable = false)
    private boolean enabled = false;

    @Column(name = "updated_by", length = 64)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    protected AiModelRoute() {
    }

    public AiModelRoute(String scenario) {
        this.scenario = scenario;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    /** 应用界面提交的路由配置（密钥另经 {@link #setKey} 加密写入）。 */
    public void apply(String provider, String baseUrl, String model, int maxTokens, boolean enabled, String actor) {
        this.provider = provider == null ? AiProviderConfig.LOCAL : provider;
        this.baseUrl = baseUrl;
        this.model = model;
        this.maxTokens = maxTokens;
        this.enabled = enabled;
        this.updatedBy = actor;
    }

    public void setKey(String apiKeyEnc, String keyHint) {
        this.apiKeyEnc = apiKeyEnc;
        this.keyHint = keyHint;
    }

    public Long getId() { return id; }
    public String getScenario() { return scenario; }
    public String getProvider() { return provider; }
    public String getBaseUrl() { return baseUrl; }
    public String getModel() { return model; }
    public int getMaxTokens() { return maxTokens; }
    public String getApiKeyEnc() { return apiKeyEnc; }
    public String getKeyHint() { return keyHint; }
    public boolean isEnabled() { return enabled; }
    public String getUpdatedBy() { return updatedBy; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
