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
 * 自定义列表视图服务（B12 Phase2 / D1-8 H-05，隔离红线核心）。
 *
 * 编排器把声明式 {@code definition}（可见列/筛选/排序）编译为参数化查询，强制：
 *  1) 字段白名单：只允许该 object_type 的标准列 + 启用 custom_field_def 键——SELECT/WHERE/ORDER
 *     里出现的每个字段都必须命中白名单，否则拒绝（杜绝注入任意列/子查询）；
 *  2) 值一律走绑定参数（从不拼接进 SQL）；
 *  3) 运行在 @Transactional（modules 包）→ OrgScopeAspect 注入 visible_orgs 的会话，
 *     asset 表 RLS 兜底 org_id = ANY(visibleOrgs)——任意视图/筛选无路径越过 visibleOrgs（TC-SEC-102）。
 *
 * 禁止裸 JDBC/超级用户/BYPASSRLS。列 SQL 表达式取自固定白名单（非用户回显），值参数化，安全。
 */
@Service
public class CustomViewService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int ROW_CAP = 500;

    /** 一个白名单列：SQL 表达式（取自固定映射）+ 逻辑类型（决定参数强转）。 */
    private record Col(String sql, String type) {
    }

    /** ASSET 标准列白名单（key → 列表达式 + 类型）。仅这些标准列可被视图引用。 */
    private static final Map<String, Col> ASSET_STD = Map.ofEntries(
            Map.entry("id", new Col("id", "NUMBER")),
            Map.entry("name", new Col("name", "TEXT")),
            Map.entry("asset_type", new Col("asset_type", "TEXT")),
            Map.entry("owner", new Col("owner", "TEXT")),
            Map.entry("classification", new Col("classification", "TEXT")),
            Map.entry("criticality", new Col("criticality", "TEXT")),
            Map.entry("status", new Col("status", "TEXT")),
            Map.entry("cia_rating", new Col("cia_rating", "TEXT")),
            Map.entry("network_zone", new Col("network_zone", "TEXT")),
            Map.entry("mlps_level", new Col("mlps_level", "NUMBER")),
            Map.entry("mlps_review_due", new Col("mlps_review_due", "DATE")),
            Map.entry("contains_pi", new Col("contains_pi", "BOOL")),
            Map.entry("cross_border", new Col("cross_border", "BOOL")),
            Map.entry("mlps_filed", new Col("mlps_filed", "BOOL")),
            Map.entry("contains_chd", new Col("contains_chd", "BOOL")));

    private static final Map<String, String> HOST_TABLE = Map.of("ASSET", "asset");

    @PersistenceContext
    private EntityManager em;

    private final CustomViewDefRepository repository;
    private final CustomFieldDefRepository fieldRepository;
    private final HashChainService hashChainService;

    public CustomViewService(CustomViewDefRepository repository, CustomFieldDefRepository fieldRepository,
                             HashChainService hashChainService) {
        this.repository = repository;
        this.fieldRepository = fieldRepository;
        this.hashChainService = hashChainService;
    }

    @Transactional(readOnly = true)
    public List<CustomViewDef> list(String objectType) {
        return repository.findByObjectTypeOrderByIdAsc(objectType);
    }

    /** 登记视图（先校验 definition 白名单，坏定义写入即拒）。 */
    @Transactional
    public CustomViewDef create(Long orgId, String objectType, String name, String definition, String actor) {
        validateDefinition(objectType, definition);   // 写入即验白名单，防脏定义
        CustomViewDef v = new CustomViewDef(orgId, objectType, name, definition, actor);
        CustomViewDef saved = repository.save(v);
        hashChainService.append(orgId, "CUSTOM_VIEW_CREATE", actor, "CUSTOM_VIEW:" + saved.getId(),
                "登记自定义视图 " + objectType + " name=" + name);
        return saved;
    }

    @Transactional
    public CustomViewDef retire(Long id, String actor) {
        CustomViewDef v = get(id);
        v.setStatus("RETIRED");
        CustomViewDef saved = repository.save(v);
        hashChainService.append(v.getOrgId(), "CUSTOM_VIEW_RETIRE", actor, "CUSTOM_VIEW:" + id, "停用自定义视图");
        return saved;
    }

    /** 执行视图（按 id）：编译为参数化查询并返回行（RLS 裁剪，行数封顶）。 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> execute(Long id) {
        CustomViewDef v = get(id);
        return runQuery(v.getObjectType(), v.getDefinition());
    }

    /** 执行临时定义（预览用，不落库）。 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> preview(String objectType, String definition) {
        return runQuery(objectType, definition);
    }

    // ---------- 编排器核心 ----------

    /** 当前可见范围内某对象的允许列集合 = 标准列 + 启用自定义字段（ext->>'key'，NUMBER 转 numeric）。 */
    private Map<String, Col> allowedColumns(String objectType) {
        Map<String, Col> allowed = new LinkedHashMap<>();
        if ("ASSET".equals(objectType)) {
            allowed.putAll(ASSET_STD);
        }
        for (CustomFieldDef d : fieldRepository.findByObjectTypeAndStatusOrderBySeqAscIdAsc(objectType, "ACTIVE")) {
            String key = d.getFieldKey();
            String type = d.getDataType();
            // 自定义字段值存 ext JSONB；NUMBER 比较转 numeric（用 CAST，避开 Hibernate 对 :: 的命名参数误判），其余按文本
            String expr = "NUMBER".equals(type)
                    ? "CAST(ext->>'" + safeKey(key) + "' AS numeric)"
                    : "ext->>'" + safeKey(key) + "'";
            allowed.put("ext." + key, new Col(expr, "NUMBER".equals(type) ? "NUMBER" : "TEXT"));
        }
        return allowed;
    }

    /** field_key 已在登记时校验为 [A-Za-z][A-Za-z0-9_]*，此处再断言一次（防越权拼入）。 */
    private String safeKey(String key) {
        if (key == null || !key.matches("[A-Za-z][A-Za-z0-9_]{0,63}")) {
            throw new IllegalArgumentException("非法自定义字段键：" + key);
        }
        return key;
    }

    /** 仅校验白名单（写入时）。 */
    private void validateDefinition(String objectType, String definition) {
        compile(objectType, definition);   // 复用编译逻辑，只为触发白名单校验
    }

    private List<Map<String, Object>> runQuery(String objectType, String definition) {
        Compiled c = compile(objectType, definition);
        @SuppressWarnings("unchecked")
        List<Object> raw = c.query.getResultList();
        List<Map<String, Object>> out = new ArrayList<>(raw.size());
        boolean single = c.columnKeys.size() == 1;   // 单列时 JPA 返回标量而非 Object[]
        for (Object r : raw) {
            Map<String, Object> row = new LinkedHashMap<>();
            if (single) {
                row.put(c.columnKeys.get(0), r);
            } else {
                Object[] arr = (Object[]) r;
                for (int i = 0; i < c.columnKeys.size(); i++) {
                    row.put(c.columnKeys.get(i), arr[i]);
                }
            }
            out.add(row);
        }
        return out;
    }

    private record Compiled(Query query, List<String> columnKeys) {
    }

    /** 声明式 definition → 参数化查询（白名单强校验 + 值绑定）。 */
    private Compiled compile(String objectType, String definition) {
        String table = HOST_TABLE.get(objectType);
        if (table == null) {
            throw new IllegalArgumentException("不支持的对象类型：" + objectType);
        }
        Map<String, Col> allowed = allowedColumns(objectType);
        JsonNode def;
        try {
            def = MAPPER.readTree(definition == null || definition.isBlank() ? "{}" : definition);
        } catch (com.fasterxml.jackson.core.JacksonException e) {
            throw new IllegalArgumentException("视图定义不是合法 JSON：" + e.getOriginalMessage());
        }

        // 1) 可见列（缺省取 name/classification/status）
        List<String> columns = new ArrayList<>();
        if (def.has("columns") && def.get("columns").isArray() && def.get("columns").size() > 0) {
            def.get("columns").forEach(n -> columns.add(n.asText()));
        } else {
            columns.addAll(List.of("name", "classification", "status"));
        }
        StringBuilder select = new StringBuilder();
        List<String> columnKeys = new ArrayList<>();
        for (String col : columns) {
            Col c = allowed.get(col);
            if (c == null) {
                throw new IllegalArgumentException("视图引用了未授权列（不在白名单）：" + col);
            }
            if (select.length() > 0) {
                select.append(", ");
            }
            select.append(c.sql());
            columnKeys.add(col);
        }

        // 2) 筛选（参数化）
        StringBuilder where = new StringBuilder();
        Map<String, Object> params = new LinkedHashMap<>();
        int pi = 0;
        if (def.has("filters") && def.get("filters").isArray()) {
            for (JsonNode f : def.get("filters")) {
                String field = f.path("field").asText("");
                String op = f.path("op").asText("eq");
                Col c = allowed.get(field);
                if (c == null) {
                    throw new IllegalArgumentException("筛选引用了未授权字段：" + field);
                }
                String pname = "p" + (pi++);
                String cond = buildCondition(c, op, pname, f.path("value"), params);
                where.append(where.length() == 0 ? " WHERE " : " AND ").append(cond);
            }
        }

        // 3) 排序（字段须白名单，方向仅 asc/desc）
        String orderBy = "";
        if (def.has("sort") && def.get("sort").isObject()) {
            String sf = def.get("sort").path("field").asText("");
            if (!sf.isEmpty()) {
                Col c = allowed.get(sf);
                if (c == null) {
                    throw new IllegalArgumentException("排序引用了未授权字段：" + sf);
                }
                String dir = "desc".equalsIgnoreCase(def.get("sort").path("dir").asText("asc")) ? "DESC" : "ASC";
                orderBy = " ORDER BY " + c.sql() + " " + dir;
            }
        }

        // asset 表 RLS 已注入 org_id = ANY(visible_orgs)，此处不再拼 org 条件（依赖 RLS 兜底）
        String sql = "SELECT " + select + " FROM " + table + where + orderBy + " LIMIT " + ROW_CAP;
        Query q = em.createNativeQuery(sql);
        params.forEach(q::setParameter);
        return new Compiled(q, columnKeys);
    }

    /** 单个筛选条件 → 参数化片段，值按列类型强转后绑定（从不拼接原值）。 */
    private String buildCondition(Col c, String op, String pname, JsonNode valueNode, Map<String, Object> params) {
        String expr = c.sql();
        switch (op) {
            case "contains":
                params.put(pname, "%" + valueNode.asText("") + "%");
                // 文本模糊：非文本列转文本再 ILIKE
                return ("TEXT".equals(c.type()) ? expr : "CAST(" + expr + " AS text)") + " ILIKE :" + pname;
            case "ne":
                params.put(pname, coerce(c.type(), valueNode));
                return expr + " <> :" + pname;
            case "gt":
                params.put(pname, coerce(c.type(), valueNode));
                return expr + " > :" + pname;
            case "lt":
                params.put(pname, coerce(c.type(), valueNode));
                return expr + " < :" + pname;
            case "gte":
                params.put(pname, coerce(c.type(), valueNode));
                return expr + " >= :" + pname;
            case "lte":
                params.put(pname, coerce(c.type(), valueNode));
                return expr + " <= :" + pname;
            case "eq":
            default:
                params.put(pname, coerce(c.type(), valueNode));
                return expr + " = :" + pname;
        }
    }

    /** 按列逻辑类型把 JSON 值强转为绑定参数（防类型不匹配 + 杜绝越权表达式）。 */
    private Object coerce(String type, JsonNode v) {
        switch (type) {
            case "NUMBER":
                return v.isNumber() ? v.numberValue() : Double.valueOf(v.asText("0"));
            case "BOOL":
                return v.isBoolean() ? v.asBoolean() : Boolean.valueOf(v.asText("false"));
            case "DATE":
                return java.time.LocalDate.parse(v.asText());
            case "TEXT":
            default:
                return v.asText("");
        }
    }

    private CustomViewDef get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("自定义视图不存在或不可见：id=" + id));
    }
}
