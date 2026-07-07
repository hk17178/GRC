package com.mandao.grc.modules.custom;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 自定义能力的**统一列白名单目录**（B12 / D1-8「统一数据访问层」单一事实源）。
 *
 * 自定义视图（H-05）、自定义报表（§六）等一切"运行期编译声明式定义为参数化查询"的能力，
 * 都必须经此目录取得可引用字段——只允许某 object_type 的标准列 + 启用 custom_field_def 键，
 * 杜绝注入任意列/子查询。列 SQL 表达式取自固定映射（非用户回显），值一律由调用方参数化绑定。
 *
 * 新增宿主对象类型时，在此登记标准列白名单 + 宿主表名即可，视图/报表自动共享同一红线。
 */
@Component
public class ObjectColumnCatalog {

    /** 一个白名单列：SQL 表达式（取自固定映射）+ 逻辑类型（决定参数强转）。 */
    public record Col(String sql, String type) {
    }

    /** ASSET 标准列白名单（key → 列表达式 + 类型）。仅这些标准列可被自定义能力引用。 */
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

    private static final Map<String, Map<String, Col>> STD_BY_OBJECT = Map.of("ASSET", ASSET_STD);
    private static final Map<String, String> HOST_TABLE = Map.of("ASSET", "asset");

    private static final java.util.Set<String> NUMERIC_AGG = java.util.Set.of("SUM", "AVG");
    private static final java.util.Set<String> ORDERED_AGG = java.util.Set.of("MIN", "MAX");   // 可作用于 NUMBER 或 DATE

    private final CustomFieldDefRepository fieldRepository;

    public ObjectColumnCatalog(CustomFieldDefRepository fieldRepository) {
        this.fieldRepository = fieldRepository;
    }

    /** 宿主表名（未登记对象类型→null，调用方须拒绝）。 */
    public String hostTable(String objectType) {
        return HOST_TABLE.get(objectType);
    }

    /**
     * 某对象在当前可见范围内的允许列集合 = 标准列 + 启用自定义字段（ext->>'key'，NUMBER 转 numeric）。
     * 须在已注入 visible_orgs 的事务内调用（自定义字段查询本身也受 RLS 裁剪）。
     */
    public Map<String, Col> allowedColumns(String objectType) {
        Map<String, Col> allowed = new LinkedHashMap<>();
        Map<String, Col> std = STD_BY_OBJECT.get(objectType);
        if (std != null) {
            allowed.putAll(std);
        }
        for (CustomFieldDef d : fieldRepository.findByObjectTypeAndStatusOrderBySeqAscIdAsc(objectType, "ACTIVE")) {
            String key = safeKey(d.getFieldKey());
            boolean num = "NUMBER".equals(d.getDataType());
            // 自定义字段值存 ext JSONB；NUMBER 用 CAST(... AS numeric)（避开 Hibernate 对 :: 的命名参数误判），其余按文本
            String expr = num ? "CAST(ext->>'" + key + "' AS numeric)" : "ext->>'" + key + "'";
            allowed.put("ext." + key, new Col(expr, num ? "NUMBER" : "TEXT"));
        }
        return allowed;
    }

    /** field_key 已在登记时校验为 [A-Za-z][A-Za-z0-9_]*，此处再断言一次（防越权拼入 SQL）。 */
    public String safeKey(String key) {
        if (key == null || !key.matches("[A-Za-z][A-Za-z0-9_]{0,63}")) {
            throw new IllegalArgumentException("非法自定义字段键：" + key);
        }
        return key;
    }

    // ---------- 共享聚合编译（报表/KPI 复用同一套聚合枚举与类型校验） ----------

    /**
     * 聚合函数 + 度量字段 → 参数化 SQL 聚合表达式（取自固定枚举/白名单，安全）。
     * COUNT 恒用 COUNT(*)；SUM/AVG 仅可作用于 NUMBER 列；MIN/MAX 限 NUMBER/DATE。未授权字段/未知函数/类型不符均拒。
     */
    public String aggregateSql(Map<String, Col> allowed, String agg, String field) {
        String a = agg == null ? "" : agg.toUpperCase();
        if ("COUNT".equals(a)) {
            return "COUNT(*)";
        }
        if (NUMERIC_AGG.contains(a) || ORDERED_AGG.contains(a)) {
            Col c = allowed.get(field);
            if (c == null) {
                throw new IllegalArgumentException("聚合引用了未授权字段：" + field);
            }
            boolean typeOk = NUMERIC_AGG.contains(a)
                    ? "NUMBER".equals(c.type())
                    : ("NUMBER".equals(c.type()) || "DATE".equals(c.type()));
            if (!typeOk) {
                throw new IllegalArgumentException(a + " 只能作用于" + (NUMERIC_AGG.contains(a) ? "数值" : "数值/日期") + "字段：" + field);
            }
            return a + "(" + c.sql() + ")";
        }
        throw new IllegalArgumentException("不支持的聚合函数：" + agg);
    }

    // ---------- 共享筛选编译（视图/报表复用同一套参数化条件） ----------

    /** 单个筛选条件 → 参数化片段，值按列类型强转后绑定（从不拼接原值）。op ∈ eq/ne/contains/gt/lt/gte/lte。 */
    public String buildCondition(Col c, String op, String pname,
                                 com.fasterxml.jackson.databind.JsonNode valueNode, Map<String, Object> params) {
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
    public Object coerce(String type, com.fasterxml.jackson.databind.JsonNode v) {
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
}
