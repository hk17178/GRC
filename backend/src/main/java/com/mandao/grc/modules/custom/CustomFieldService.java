package com.mandao.grc.modules.custom;

import com.mandao.grc.modules.audit.HashChainService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 自定义字段服务（B12 低代码 Phase1 / D1-8 H-04）。
 *
 * 隔离/留痕范式同其它模块：@Transactional → 切面注入 visible_orgs，RLS 裁剪 + WITH CHECK；
 * 字段登记/停用留痕入链。另提供 {@link #validateExt} 供宿主服务（如 AssetService）在写入前
 * 按启用字段定义校验 ext 值（类型/必填/选项白名单），保证自定义值也受约束、不成脏数据。
 */
@Service
public class CustomFieldService {

    private final CustomFieldDefRepository repository;
    private final HashChainService hashChainService;

    public CustomFieldService(CustomFieldDefRepository repository, HashChainService hashChainService) {
        this.repository = repository;
        this.hashChainService = hashChainService;
    }

    @Transactional(readOnly = true)
    public List<CustomFieldDef> list(String objectType) {
        return repository.findByObjectTypeOrderBySeqAscIdAsc(objectType);
    }

    @Transactional(readOnly = true)
    public List<CustomFieldDef> listActive(String objectType) {
        return repository.findByObjectTypeAndStatusOrderBySeqAscIdAsc(objectType, "ACTIVE");
    }

    /** 登记一个自定义字段。field_key 须为合法标识符且同 org+object 内唯一（DB 唯一约束兜底）。 */
    @Transactional
    public CustomFieldDef create(Long orgId, String objectType, String fieldKey, String label,
                                 CustomFieldDef.DataType dataType, String options, boolean required,
                                 boolean sensitive, boolean aggregatable, int seq, String actor) {
        if (fieldKey == null || !fieldKey.matches("[A-Za-z][A-Za-z0-9_]{0,63}")) {
            throw new IllegalArgumentException("字段键须以字母开头、仅含字母/数字/下划线：" + fieldKey);
        }
        if (dataType == CustomFieldDef.DataType.SELECT && (options == null || options.isBlank())) {
            throw new IllegalArgumentException("SELECT 类型须提供选项（分号分隔）");
        }
        CustomFieldDef d = new CustomFieldDef(orgId, objectType, fieldKey, label, dataType,
                options, required, sensitive, aggregatable, seq, actor);
        CustomFieldDef saved = repository.save(d);
        hashChainService.append(orgId, "CUSTOM_FIELD_CREATE", actor, "CUSTOM_FIELD:" + saved.getId(),
                "登记自定义字段 " + objectType + "." + fieldKey + " 类型=" + dataType);
        return saved;
    }

    /** 停用字段（置 RETIRED；不删 ext 历史值，随宿主保留）。 */
    @Transactional
    public CustomFieldDef retire(Long id, String actor) {
        CustomFieldDef d = get(id);
        d.setStatus("RETIRED");
        CustomFieldDef saved = repository.save(d);
        hashChainService.append(d.getOrgId(), "CUSTOM_FIELD_RETIRE", actor, "CUSTOM_FIELD:" + id, "停用自定义字段");
        return saved;
    }

    /**
     * 校验 ext 值集合（宿主写入前调用）：
     *  - 只允许启用字段的键（未登记键拒绝，杜绝任意键污染）；
     *  - required 字段不得为空；
     *  - 按 data_type 校验（NUMBER 为数、BOOL 为 true/false、SELECT 为选项之一）；
     *  返回按类型规整后的值 Map（NUMBER→Number，BOOL→Boolean），供宿主原样落 ext JSONB。
     */
    @Transactional(readOnly = true)
    public Map<String, Object> validateExt(String objectType, Map<String, Object> ext) {
        List<CustomFieldDef> defs = listActive(objectType);
        Map<String, CustomFieldDef> byKey = new java.util.HashMap<>();
        for (CustomFieldDef d : defs) {
            byKey.put(d.getFieldKey(), d);
        }
        Map<String, Object> in = ext == null ? Map.of() : ext;
        // 未登记键拒绝
        for (String k : in.keySet()) {
            if (!byKey.containsKey(k)) {
                throw new IllegalArgumentException("未登记的自定义字段键：" + k);
            }
        }
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        for (CustomFieldDef d : defs) {
            Object v = in.get(d.getFieldKey());
            boolean empty = v == null || String.valueOf(v).isBlank();
            if (empty) {
                if (d.isRequired()) {
                    throw new IllegalArgumentException("自定义字段「" + d.getLabel() + "」必填");
                }
                continue;
            }
            out.put(d.getFieldKey(), coerce(d, v));
        }
        return out;
    }

    private Object coerce(CustomFieldDef d, Object v) {
        String s = String.valueOf(v).trim();
        switch (CustomFieldDef.DataType.valueOf(d.getDataType())) {
            case NUMBER:
                try {
                    return Double.valueOf(s);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("自定义字段「" + d.getLabel() + "」须为数值：" + s);
                }
            case BOOL:
                if (v instanceof Boolean b) {
                    return b;
                }
                if ("true".equalsIgnoreCase(s) || "false".equalsIgnoreCase(s)) {
                    return Boolean.valueOf(s.toLowerCase());
                }
                throw new IllegalArgumentException("自定义字段「" + d.getLabel() + "」须为布尔：" + s);
            case SELECT:
                for (String opt : (d.getOptions() == null ? "" : d.getOptions()).split(";")) {
                    if (opt.trim().equals(s)) {
                        return s;
                    }
                }
                throw new IllegalArgumentException("自定义字段「" + d.getLabel() + "」取值不在选项内：" + s);
            case DATE:
            case TEXT:
            default:
                return s;
        }
    }

    private CustomFieldDef get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("自定义字段不存在或不可见：id=" + id));
    }
}
