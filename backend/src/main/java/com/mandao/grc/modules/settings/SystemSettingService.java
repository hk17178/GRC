package com.mandao.grc.modules.settings;

import com.mandao.grc.modules.audit.HashChainService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 系统设置业务服务（租户配置 / D1-8 可配置性）。
 *
 * 隔离/留痕范式同其它模块：方法 @Transactional 且位于 com.mandao.grc.modules 包，切面注入 visible_orgs，
 * RLS 裁剪 + 写校验；配置变更经 {@link HashChainService#append} 入哈希链（配置变更可审计）。
 *
 * ===== 红线 =====
 *  1) 系统锁定项 editable=false 不可修改（{@link #update} 拒绝）；
 *  2) 取值须符合声明的 valueType（INT/BOOL 类型校验）。
 *
 * 设计依据：需求文档 系统设置、D1-8 可配置性低代码专项、D2-5。
 */
@Service
public class SystemSettingService {

    private final SystemSettingRepository repository;
    private final HashChainService hashChainService;

    public SystemSettingService(SystemSettingRepository repository, HashChainService hashChainService) {
        this.repository = repository;
        this.hashChainService = hashChainService;
    }

    @Transactional(readOnly = true)
    public List<SystemSetting> list() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public List<SystemSetting> listByCategory(String category) {
        return repository.findByCategory(category);
    }

    /** 按键读取（可见范围内首条；集团级键须在 org=1 上下文调用）。 */
    @Transactional(readOnly = true)
    public java.util.Optional<SystemSetting> findByKey(String key) {
        return repository.findFirstBySettingKey(key);
    }

    @Transactional(readOnly = true)
    public SystemSetting get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("配置项不存在或不可见：id=" + id));
    }

    /** 定义一个配置项。取值须符合声明类型。 */
    @Transactional
    public SystemSetting define(Long orgId, String key, String value, SettingValueType valueType,
                                String category, String description, boolean editable, String actor) {
        validateType(valueType, value);
        SystemSetting saved = repository.save(
                new SystemSetting(orgId, key, value, valueType, category, description, editable));
        hashChainService.append(orgId, "SETTING_DEFINE", actor, "SETTING:" + saved.getId(),
                "定义配置 key=" + key + " 类型=" + valueType + " 可改=" + editable);
        return saved;
    }

    /**
     * 更新配置取值。
     * 【红线】系统锁定项(editable=false)不可改；取值须符合声明类型。配置变更入链留痕。
     */
    @Transactional
    public SystemSetting update(Long id, String newValue, String actor) {
        SystemSetting s = get(id);
        if (!s.isEditable()) {
            throw new IllegalStateException("系统锁定配置项不可修改：key=" + s.getSettingKey());
        }
        validateType(s.getValueType(), newValue);
        String old = s.getSettingValue();
        s.setSettingValue(newValue);
        SystemSetting saved = repository.save(s);
        hashChainService.append(s.getOrgId(), "SETTING_UPDATE", actor, "SETTING:" + id,
                "修改配置 key=" + s.getSettingKey() + " 旧=" + old + " 新=" + newValue);
        return saved;
    }

    /** 按声明类型校验取值合法性（STRING/JSON 不强校验；INT/BOOL 强校验）。 */
    private void validateType(SettingValueType type, String value) {
        if (value == null) {
            return;
        }
        switch (type) {
            case INT -> {
                try {
                    Integer.parseInt(value.trim());
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("配置值非整数：" + value);
                }
            }
            case BOOL -> {
                String v = value.trim();
                if (!v.equalsIgnoreCase("true") && !v.equalsIgnoreCase("false")) {
                    throw new IllegalArgumentException("配置值非布尔(true/false)：" + value);
                }
            }
            default -> {
                // STRING / JSON 不在此强校验
            }
        }
    }
}
