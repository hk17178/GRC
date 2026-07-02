package com.mandao.grc.modules.ai;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * AI 治理服务（V42）：模型白名单 + 提示词模板 CRUD 与白名单校验。
 *
 * 平台级数据（无 RLS）；写操作由控制器 @RequiresPermission("ai") 门控。
 */
@Service
public class AiGovernanceService {

    private final AiGovernanceRepository repo;

    public AiGovernanceService(AiGovernanceRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public List<AiGovernance> listByKind(String kind) {
        return repo.findByKindOrderByIdAsc(kind);
    }

    @Transactional
    public AiGovernance create(String kind, String name, String detail, String actor) {
        if (!AiGovernance.KIND_MODEL_WHITELIST.equals(kind) && !AiGovernance.KIND_PROMPT_TEMPLATE.equals(kind)) {
            throw new IllegalArgumentException("未知治理条目类型：" + kind);
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("名称不能为空");
        }
        return repo.save(new AiGovernance(kind, name.trim(), detail, actor));
    }

    @Transactional
    public AiGovernance update(Long id, String name, String detail, String actor) {
        AiGovernance g = get(id);
        g.update(name, detail, actor);
        return repo.save(g);
    }

    @Transactional
    public AiGovernance setEnabled(Long id, boolean enabled) {
        AiGovernance g = get(id);
        g.setEnabled(enabled);
        return repo.save(g);
    }

    @Transactional
    public void delete(Long id) {
        repo.delete(get(id));
    }

    /**
     * 白名单校验：存在启用的白名单条目时，model 必须命中其一；
     * 无任何启用条目 = 未启用白名单管控（放行）。LOCAL 离线模式不校验。
     */
    @Transactional(readOnly = true)
    public void checkModelAllowed(String provider, String model) {
        if (provider == null || "LOCAL".equalsIgnoreCase(provider)) {
            return;
        }
        List<AiGovernance> whitelist = repo.findByKindAndEnabledTrue(AiGovernance.KIND_MODEL_WHITELIST);
        if (whitelist.isEmpty()) {
            return;
        }
        boolean allowed = model != null && whitelist.stream().anyMatch(w -> w.getName().equalsIgnoreCase(model.trim()));
        if (!allowed) {
            throw new IllegalArgumentException("模型「" + model + "」不在白名单内，请先在 AI 治理中加入白名单或改用白名单内模型");
        }
    }

    private AiGovernance get(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("治理条目不存在：id=" + id));
    }
}
