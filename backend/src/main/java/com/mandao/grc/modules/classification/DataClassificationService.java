package com.mandao.grc.modules.classification;

import com.mandao.grc.common.isolation.IsolationContext;
import com.mandao.grc.common.privacy.SensitiveDataMasker;
import com.mandao.grc.modules.custom.CustomFieldDef;
import com.mandao.grc.modules.custom.CustomFieldDefRepository;
import com.mandao.grc.modules.rbac.RbacPermissionService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 数据分级引擎（B30 · 合规二期，三级访问控制 + 敏感访问留痕）。
 *
 * 定位：为「运行期读取自定义字段」的能力（自定义视图 H-05 / 报表）加一层<b>字段级密级门控</b>——
 *  · 数据分级：字段是否敏感取自 custom_field_def.is_sensitive（客户登记字段时勾选）；
 *  · 主体密级（clearance）：持有 RBAC 资源 {@code org.viewSensitive} 者为 SENSITIVE，否则 INTERNAL（默认拒绝）；
 *  · 门控：主体密级 &lt; SENSITIVE 时，敏感字段值一律脱敏（已知 PII 保留头尾，其余整体抹除）；
 *  · 留痕：凡命中敏感字段的访问（无论放行/脱敏）都写 sensitive_access_log（谁/何时/读了哪些字段/放行还是脱敏）。
 *
 * 隔离红线：方法在 modules 包 @Transactional → OrgScopeAspect 注入 visible_orgs；
 * 字段定义查询与留痕落库都受 RLS 裁剪，留痕锚定操作者主可见组织（其审计域），绝不写越域行。
 */
@Service
public class DataClassificationService {

    @PersistenceContext
    private EntityManager em;

    private final CustomFieldDefRepository fieldRepository;
    private final RbacPermissionService permissionService;

    public DataClassificationService(CustomFieldDefRepository fieldRepository,
                                     RbacPermissionService permissionService) {
        this.fieldRepository = fieldRepository;
        this.permissionService = permissionService;
    }

    /** 主体数据密级：持有 org.viewSensitive（RO/RW 皆可）→ SENSITIVE，否则 INTERNAL（默认拒绝）。 */
    public DataLevel clearanceOf(String username) {
        if (username == null || username.isBlank()) {
            return DataLevel.INTERNAL;
        }
        return permissionService.effectiveFor(username).containsKey("org.viewSensitive")
                ? DataLevel.SENSITIVE : DataLevel.INTERNAL;
    }

    /**
     * 对一批查询结果行按敏感字段做密级门控（就地脱敏）并留痕。
     *
     * @param objectType 宿主对象类型（决定敏感字段集合）
     * @param columnKeys 结果列键（自定义字段以 ext.&lt;key&gt; 出现）
     * @param rows       查询结果（就地改写敏感列）
     * @param actor      访问者用户名（决定密级）
     * @return 门控后的行（无敏感列命中时原样返回，不留痕）
     */
    @Transactional
    public List<Map<String, Object>> screen(String objectType, List<String> columnKeys,
                                            List<Map<String, Object>> rows, String actor) {
        // 该对象类型下被标记敏感的自定义字段 → 结果列键 ext.<key>
        Set<String> sensitiveCols = new LinkedHashSet<>();
        for (CustomFieldDef d : fieldRepository.findByObjectTypeAndStatusOrderBySeqAscIdAsc(objectType, "ACTIVE")) {
            if (d.isSensitive()) {
                sensitiveCols.add("ext." + d.getFieldKey());
            }
        }
        List<String> present = new ArrayList<>();
        for (String key : columnKeys) {
            if (sensitiveCols.contains(key)) {
                present.add(key);
            }
        }
        if (present.isEmpty() || rows.isEmpty()) {
            return rows;   // 视图未选任何敏感列 → 不门控、不留痕（无噪声）
        }

        DataLevel clearance = clearanceOf(actor);
        boolean granted = clearance.atLeast(DataLevel.SENSITIVE);
        if (!granted) {
            for (Map<String, Object> row : rows) {
                for (String col : present) {
                    Object v = row.get(col);
                    if (v != null && !String.valueOf(v).isEmpty()) {
                        row.put(col, redact(String.valueOf(v)));
                    }
                }
            }
        }
        logAccess(objectType, present, rows.size(), clearance, granted, actor);
        return rows;
    }

    /** 敏感字段脱敏：已知 PII 模式保留头尾（复用 B29/B30 掩码器），其余整体抹除（防非 PII 密文泄露）。 */
    private String redact(String s) {
        return SensitiveDataMasker.containsSensitive(s) ? SensitiveDataMasker.mask(s) : "******";
    }

    /** 敏感访问落库（在 screen 事务内，RLS 锚定操作者主可见组织）。 */
    private void logAccess(String objectType, List<String> fields, int rowCount,
                           DataLevel clearance, boolean granted, String actor) {
        List<Long> visible = IsolationContext.get();
        if (visible == null || visible.isEmpty()) {
            return;   // 无隔离上下文（理论不至）→ 放弃留痕，绝不写越域行
        }
        em.createNativeQuery("INSERT INTO sensitive_access_log"
                        + "(org_id, username, object_type, field_keys, row_count, clearance, action) "
                        + "VALUES (:org, :u, :ot, :fk, :rc, :cl, :ac)")
                .setParameter("org", visible.get(0))
                .setParameter("u", actor == null ? "" : actor)
                .setParameter("ot", objectType)
                .setParameter("fk", String.join(",", fields))
                .setParameter("rc", rowCount)
                .setParameter("cl", clearance.name())
                .setParameter("ac", granted ? "GRANTED" : "MASKED")
                .executeUpdate();
    }

    /** 敏感访问留痕查询（审计用，最近 limit 条；RLS 仅回当前可见域）。 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> recentLog(int limit) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                        "SELECT id, org_id, username, object_type, field_keys, row_count, clearance, action, "
                                + "CAST(EXTRACT(EPOCH FROM accessed_at) * 1000 AS bigint) "
                                + "FROM sensitive_access_log ORDER BY id DESC LIMIT " + Math.min(Math.max(limit, 1), 200))
                .getResultList();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object[] r : rows) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", ((Number) r[0]).longValue());
            m.put("orgId", ((Number) r[1]).longValue());
            m.put("username", r[2]);
            m.put("objectType", r[3]);
            m.put("fieldKeys", r[4]);
            m.put("rowCount", ((Number) r[5]).intValue());
            m.put("clearance", r[6]);
            m.put("action", r[7]);
            m.put("accessedAtMs", ((Number) r[8]).longValue());
            out.add(m);
        }
        return out;
    }
}
