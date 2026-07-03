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
    private final AiModelRouteRepository routeRepo;
    private final ConfigCrypto crypto;
    private final AiGovernanceService governance;

    public AiConfigService(AiProviderConfigRepository repo, AiModelRouteRepository routeRepo,
                           ConfigCrypto crypto, AiGovernanceService governance) {
        this.repo = repo;
        this.routeRepo = routeRepo;
        this.crypto = crypto;
        this.governance = governance;
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
        // 模型白名单管控（V42）：存在启用的白名单时，非 LOCAL 模型必须命中
        governance.checkModelAllowed(provider, model);
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

    // ===== 场景模型路由（V49 模型分配）=====

    /** 路由视图（无密钥明文）。 */
    public record RouteView(String scenario, String provider, String baseUrl, String model, int maxTokens,
                            boolean enabled, boolean keyConfigured, String keyHint) {
    }

    /** 全部场景的路由视图（未配置的场景给默认停用行，前端恒见四行）。 */
    @Transactional(readOnly = true)
    public java.util.List<RouteView> listRoutes() {
        java.util.List<RouteView> out = new java.util.ArrayList<>();
        for (AiScenario sc : AiScenario.values()) {
            AiModelRoute r = routeRepo.findByScenario(sc.name()).orElse(null);
            if (r == null) {
                out.add(new RouteView(sc.name(), AiProviderConfig.LOCAL, null, null, 1024, false, false, null));
            } else {
                boolean keyConfigured = r.getApiKeyEnc() != null && !r.getApiKeyEnc().isBlank();
                out.add(new RouteView(sc.name(), r.getProvider(), r.getBaseUrl(), r.getModel(),
                        r.getMaxTokens(), r.isEnabled(), keyConfigured, r.getKeyHint()));
            }
        }
        return out;
    }

    /** 保存某场景的路由（白名单管控同样生效；apiKey 留空=不改）。 */
    @Transactional
    public java.util.List<RouteView> updateRoute(String scenario, String provider, String baseUrl, String model,
                                                 Integer maxTokens, boolean enabled, String apiKeyPlain, String actor) {
        AiScenario.valueOf(scenario); // 非法场景直接抛 IllegalArgumentException
        governance.checkModelAllowed(provider, model);
        AiModelRoute r = routeRepo.findByScenario(scenario).orElseGet(() -> new AiModelRoute(scenario));
        r.apply(provider, baseUrl, model, maxTokens == null ? 1024 : maxTokens, enabled, actor);
        if (apiKeyPlain != null && !apiKeyPlain.isBlank()) {
            r.setKey(crypto.encrypt(apiKeyPlain.trim()), crypto.hint(apiKeyPlain.trim()));
        }
        routeRepo.save(r);
        return listRoutes();
    }

    /**
     * 场景快照：该场景路由 enabled 且（LOCAL 或已配密钥）→ 用路由；否则回退全局 {@link #snapshot()}。
     * LOCAL 路由=显式让该场景走本地离线（即使全局接了大模型）。
     */
    @Transactional(readOnly = true)
    public Snapshot snapshotFor(String scenario) {
        AiModelRoute r = scenario == null ? null : routeRepo.findByScenario(scenario).orElse(null);
        if (r == null || !r.isEnabled()) {
            return snapshot();
        }
        if (AiProviderConfig.LOCAL.equalsIgnoreCase(r.getProvider())) {
            return new Snapshot(AiProviderConfig.LOCAL, null, null, r.getMaxTokens(), true, null);
        }
        String key = null;
        if (r.getApiKeyEnc() != null && !r.getApiKeyEnc().isBlank()) {
            try {
                key = crypto.decrypt(r.getApiKeyEnc());
            } catch (Exception e) {
                key = null;
            }
        }
        if (key == null) {
            return snapshot(); // 路由启用但密钥不可用 → 回退全局，避免场景瘫痪
        }
        return new Snapshot(r.getProvider(), r.getBaseUrl(), r.getModel(), r.getMaxTokens(), true, key);
    }

    /** 界面视图 DTO（无密钥明文）。 */
    public record ConfigView(String provider, String baseUrl, String model, int maxTokens, boolean enabled,
                             boolean keyConfigured, String keyHint, String updatedBy, String updatedAt) {
    }

    /** 运行期快照（含解密密钥）。 */
    public record Snapshot(String provider, String baseUrl, String model, int maxTokens, boolean enabled, String apiKey) {
    }
}
