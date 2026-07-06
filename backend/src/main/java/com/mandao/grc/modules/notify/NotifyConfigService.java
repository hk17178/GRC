package com.mandao.grc.modules.notify;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 通知中心配置服务（场景/规则/通道 CRUD）。
 *
 * 隔离：方法 @Transactional 且位于 modules 包，OrgScopeAspect 注入 visible_orgs，RLS 自动裁剪/校验。
 */
@Service
public class NotifyConfigService {

    /** A25：通知规则支持的数据源（与 NotifyRuleEngine 的 switch 分支一致）。 */
    private static final java.util.Set<String> RULE_SOURCES = java.util.Set.of(
            "REMEDIATION_OVERDUE", "ASSESSMENT_STALLED", "REG_NEW", "KRI_BREACH");

    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private final NotifyConfigRepository repo;

    public NotifyConfigService(NotifyConfigRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public List<NotifyConfig> listByKind(String kind) {
        return repo.findByKindOrderByIdAsc(kind);
    }

    @Transactional
    public NotifyConfig create(Long orgId, String kind, String name, String detail) {
        validate(kind, detail);
        return repo.save(new NotifyConfig(orgId, kind, name, detail));
    }

    @Transactional
    public NotifyConfig update(Long id, String name, String detail) {
        NotifyConfig c = get(id);
        validate(c.getKind(), detail);
        c.update(name, detail);
        return repo.save(c);
    }

    /**
     * A25：写入即校验规则配置 JSON——坏配置（未知数据源/空模板/非法 JSON）应在保存时被拒，
     * 而非等到运行期被引擎静默跳过（脏规则藏在库里，运维以为已生效却从不触发）。
     * 仅对 kind=RULE 强校验；其余 kind（场景/通道）暂不强约束 detail。
     */
    private void validate(String kind, String detail) {
        if (!"RULE".equals(kind)) {
            return;
        }
        com.fasterxml.jackson.databind.JsonNode cfg;
        try {
            cfg = MAPPER.readTree(detail == null || detail.isBlank() ? "{}" : detail);
        } catch (com.fasterxml.jackson.core.JacksonException e) {
            throw new IllegalArgumentException("通知规则配置不是合法 JSON：" + e.getOriginalMessage());
        }
        String source = cfg.path("source").asText("");
        if (!RULE_SOURCES.contains(source)) {
            throw new IllegalArgumentException("通知规则数据源未知或缺失：source=\"" + source
                    + "\"，须为 " + RULE_SOURCES);
        }
        if (cfg.path("template").asText("").isBlank()) {
            throw new IllegalArgumentException("通知规则模板(template)不能为空，否则运行期将被静默跳过");
        }
    }

    @Transactional
    public NotifyConfig setEnabled(Long id, boolean enabled) {
        NotifyConfig c = get(id);
        c.setEnabled(enabled);
        return repo.save(c);
    }

    @Transactional
    public void delete(Long id) {
        repo.delete(get(id));
    }

    private NotifyConfig get(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("通知配置不存在或不可见：id=" + id));
    }
}
