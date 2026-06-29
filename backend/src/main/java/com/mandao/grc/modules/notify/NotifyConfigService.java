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
        return repo.save(new NotifyConfig(orgId, kind, name, detail));
    }

    @Transactional
    public NotifyConfig update(Long id, String name, String detail) {
        NotifyConfig c = get(id);
        c.update(name, detail);
        return repo.save(c);
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
