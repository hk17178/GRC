package com.mandao.grc.modules.ai;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 大模型接入配置服务（平台级单行）。
 *
 * 提供：界面读取（掩码，不回显密钥）、更新（密钥加密落库）、运行期取解密快照供 Provider 调用。
 * 写操作的功能权限由控制器 @RequiresPermission("ai") 门控。
 */
@Service
public class AiConfigService {

    private final AiProviderConfigRepository repo;
    private final ConfigCrypto crypto;

    public AiConfigService(AiProviderConfigRepository repo, ConfigCrypto crypto) {
        this.repo = repo;
        this.crypto = crypto;
    }

    private AiProviderConfig load() {
        // 迁移已插入 id=1；orElseGet 仅为防御（同包可直接 new，id 默认 1）。
        return repo.findById(1L).orElseGet(() -> repo.save(new AiProviderConfig()));
    }

    /** 界面视图：不含密钥明文，仅标识是否已配置 + 末位掩码。 */
    @Transactional(readOnly = true)
    public ConfigView view() {
        AiProviderConfig c = load();
        boolean keyConfigured = c.getApiKeyEnc() != null && !c.getApiKeyEnc().isBlank();
        return new ConfigView(c.getProvider(), c.getBaseUrl(), c.getModel(), c.getMaxTokens(),
                c.isEnabled(), keyConfigured, c.getKeyHint(), c.getUpdatedBy(),
                c.getUpdatedAt() == null ? null : c.getUpdatedAt().toString());
    }

    /**
     * 更新配置。
     *
     * @param apiKeyPlain 新密钥明文；为 null/空白表示"保持原密钥不变"（便于改其他项而不重输密钥）。
     */
    @Transactional
    public ConfigView update(String provider, String baseUrl, String model, int maxTokens,
                             boolean enabled, String apiKeyPlain, String actor) {
        AiProviderConfig c = load();
        c.apply(provider, baseUrl, model, maxTokens, enabled, actor);
        if (apiKeyPlain != null && !apiKeyPlain.isBlank()) {
            c.setKey(crypto.encrypt(apiKeyPlain.trim()), crypto.hint(apiKeyPlain.trim()));
        }
        repo.save(c);
        return view();
    }

    /** 运行期快照（含解密密钥，仅供 Provider 内部调用，切勿外泄）。 */
    @Transactional(readOnly = true)
    public Snapshot snapshot() {
        AiProviderConfig c = load();
        String key = null;
        if (c.getApiKeyEnc() != null && !c.getApiKeyEnc().isBlank()) {
            try {
                key = crypto.decrypt(c.getApiKeyEnc());
            } catch (Exception e) {
                key = null; // 解密失败（如换了主密钥）→ 视为未配置密钥
            }
        }
        return new Snapshot(c.getProvider(), c.getBaseUrl(), c.getModel(), c.getMaxTokens(), c.isEnabled(), key);
    }

    /** 界面视图 DTO（无密钥明文）。 */
    public record ConfigView(String provider, String baseUrl, String model, int maxTokens, boolean enabled,
                             boolean keyConfigured, String keyHint, String updatedBy, String updatedAt) {
    }

    /** 运行期快照（含解密密钥）。 */
    public record Snapshot(String provider, String baseUrl, String model, int maxTokens, boolean enabled, String apiKey) {
    }
}
