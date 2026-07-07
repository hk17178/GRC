package com.mandao.grc.modules.custom;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mandao.grc.modules.audit.HashChainService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 自定义报表服务（B12 Phase3 / D1-8 §六）。
 *
 * 编排器把声明式 {@code definition}（维度 groupBy + 度量 measures + 筛选）编译为参数化聚合查询，强制：
 *  1) 维度/度量字段一律经 {@link ObjectColumnCatalog} 白名单（标准列 + 启用 custom_field_def 键），
 *     未授权字段拒绝——与视图共享同一「统一数据访问层」红线；
 *  2) 聚合函数仅限枚举 COUNT/SUM/AVG/MIN/MAX；SUM/AVG 仅可作用于 NUMBER 列，MIN/MAX 限 NUMBER/DATE；
 *  3) 筛选值一律绑定参数；数据集运行在已注入 visible_orgs 的 RLS 会话（宿主表 RLS 兜底）——
 *     报表数据集无路径越过 visibleOrgs；
 *  4) 导出动作入 operation_log（哈希链留痕）。
 *
 * 禁止裸 JDBC/超级用户/BYPASSRLS。SQL 结构取自固定白名单与枚举，值参数化，安全。
 */
@Service
public class CustomReportService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int ROW_CAP = 500;

    @PersistenceContext
    private EntityManager em;

    private final CustomReportDefRepository repository;
    private final ObjectColumnCatalog catalog;
    private final HashChainService hashChainService;

    public CustomReportService(CustomReportDefRepository repository, ObjectColumnCatalog catalog,
                               HashChainService hashChainService) {
        this.repository = repository;
        this.catalog = catalog;
        this.hashChainService = hashChainService;
    }

    @Transactional(readOnly = true)
    public List<CustomReportDef> list(String objectType) {
        return repository.findByObjectTypeOrderByIdAsc(objectType);
    }

    /** 登记报表（先校验 definition 白名单/聚合枚举，坏定义写入即拒）。 */
    @Transactional
    public CustomReportDef create(Long orgId, String objectType, String name, String definition, String actor) {
        compile(objectType, definition);   // 写入即验白名单+聚合枚举，防脏定义
        CustomReportDef r = new CustomReportDef(orgId, objectType, name, definition, actor);
        CustomReportDef saved = repository.save(r);
        hashChainService.append(orgId, "CUSTOM_REPORT_CREATE", actor, "CUSTOM_REPORT:" + saved.getId(),
                "登记自定义报表 " + objectType + " name=" + name);
        return saved;
    }

    @Transactional
    public CustomReportDef retire(Long id, String actor) {
        CustomReportDef r = get(id);
        r.setStatus("RETIRED");
        CustomReportDef saved = repository.save(r);
        hashChainService.append(r.getOrgId(), "CUSTOM_REPORT_RETIRE", actor, "CUSTOM_REPORT:" + id, "停用自定义报表");
        return saved;
    }

    /** 执行报表（按 id）：编译为参数化聚合查询并返回行（RLS 裁剪，行数封顶）。 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> execute(Long id) {
        CustomReportDef r = get(id);
        return runQuery(r.getObjectType(), r.getDefinition());
    }

    /** 预览临时定义（不落库，供构建器即时查看）。 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> preview(String objectType, String definition) {
        return runQuery(objectType, definition);
    }

    /** 导出报表：执行并把导出动作入 operation_log（哈希链留痕），返回行供前端生成 CSV。 */
    @Transactional
    public List<Map<String, Object>> export(Long id, String actor) {
        CustomReportDef r = get(id);
        List<Map<String, Object>> rows = runQuery(r.getObjectType(), r.getDefinition());
        hashChainService.append(r.getOrgId(), "CUSTOM_REPORT_EXPORT", actor, "CUSTOM_REPORT:" + id,
                "导出自定义报表 " + r.getName() + "（" + rows.size() + " 行）");
        return rows;
    }

    // ---------- 编排器核心 ----------

    private List<Map<String, Object>> runQuery(String objectType, String definition) {
        Compiled c = compile(objectType, definition);
        @SuppressWarnings("unchecked")
        List<Object> raw = c.query.getResultList();
        List<Map<String, Object>> out = new ArrayList<>(raw.size());
        boolean single = c.columnKeys.size() == 1;   // 单列时 JPA 返回标量而非 Object[]
        for (Object row : raw) {
            Map<String, Object> m = new LinkedHashMap<>();
            if (single) {
                m.put(c.columnKeys.get(0), row);
            } else {
                Object[] arr = (Object[]) row;
                for (int i = 0; i < c.columnKeys.size(); i++) {
                    m.put(c.columnKeys.get(i), arr[i]);
                }
            }
            out.add(m);
        }
        return out;
    }

    private record Compiled(Query query, List<String> columnKeys) {
    }

    /** 声明式 definition → 参数化聚合查询（维度/度量白名单 + 聚合枚举 + 值绑定）。 */
    private Compiled compile(String objectType, String definition) {
        String table = catalog.hostTable(objectType);
        if (table == null) {
            throw new IllegalArgumentException("不支持的对象类型：" + objectType);
        }
        Map<String, ObjectColumnCatalog.Col> allowed = catalog.allowedColumns(objectType);
        JsonNode def;
        try {
            def = MAPPER.readTree(definition == null || definition.isBlank() ? "{}" : definition);
        } catch (com.fasterxml.jackson.core.JacksonException e) {
            throw new IllegalArgumentException("报表定义不是合法 JSON：" + e.getOriginalMessage());
        }

        StringBuilder select = new StringBuilder();
        StringBuilder groupBy = new StringBuilder();
        List<String> columnKeys = new ArrayList<>();

        // 1) 维度（groupBy）：每个字段须白名单；同时进 SELECT 与 GROUP BY
        List<String> dims = new ArrayList<>();
        if (def.has("groupBy") && def.get("groupBy").isArray()) {
            def.get("groupBy").forEach(n -> dims.add(n.asText()));
        }
        for (String dim : dims) {
            ObjectColumnCatalog.Col c = allowed.get(dim);
            if (c == null) {
                throw new IllegalArgumentException("报表维度引用了未授权字段：" + dim);
            }
            if (select.length() > 0) {
                select.append(", ");
            }
            select.append(c.sql());
            groupBy.append(groupBy.length() == 0 ? " GROUP BY " : ", ").append(c.sql());
            columnKeys.add(dim);
        }

        // 2) 度量（measures）：聚合函数枚举 + 度量字段类型校验；缺省给 COUNT(*)
        List<JsonNode> measures = new ArrayList<>();
        if (def.has("measures") && def.get("measures").isArray() && def.get("measures").size() > 0) {
            def.get("measures").forEach(measures::add);
        }
        if (measures.isEmpty()) {
            measures.add(MAPPER.createObjectNode().put("agg", "count"));
        }
        for (JsonNode m : measures) {
            String agg = m.path("agg").asText("count").toUpperCase();
            String field = m.path("field").asText("");
            // 聚合表达式取自共享目录（枚举 + 白名单 + 类型校验，报表/KPI 同一套）
            String expr = catalog.aggregateSql(allowed, agg, field);
            String key = "COUNT".equals(agg) ? "count" : agg.toLowerCase() + "_" + field;
            if (select.length() > 0) {
                select.append(", ");
            }
            select.append(expr);
            columnKeys.add(key);
        }

        // 3) 筛选（参数化，复用统一条件编译）
        StringBuilder where = new StringBuilder();
        Map<String, Object> params = new LinkedHashMap<>();
        int pi = 0;
        if (def.has("filters") && def.get("filters").isArray()) {
            for (JsonNode f : def.get("filters")) {
                String fld = f.path("field").asText("");
                String op = f.path("op").asText("eq");
                ObjectColumnCatalog.Col c = allowed.get(fld);
                if (c == null) {
                    throw new IllegalArgumentException("报表筛选引用了未授权字段：" + fld);
                }
                String pname = "p" + (pi++);
                String cond = catalog.buildCondition(c, op, pname, f.path("value"), params);
                where.append(where.length() == 0 ? " WHERE " : " AND ").append(cond);
            }
        }

        // 宿主表 RLS 已注入 org_id = ANY(visible_orgs)，此处不再拼 org 条件（依赖 RLS 兜底）。
        // 按第一维度排序，稳定输出。
        String orderBy = dims.isEmpty() ? "" : " ORDER BY 1";
        String sql = "SELECT " + select + " FROM " + table + where + groupBy + orderBy + " LIMIT " + ROW_CAP;
        Query q = em.createNativeQuery(sql);
        params.forEach(q::setParameter);
        return new Compiled(q, columnKeys);
    }

    private CustomReportDef get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("自定义报表不存在或不可见：id=" + id));
    }
}
