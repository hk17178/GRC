package com.mandao.grc.modules.custom;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mandao.grc.modules.audit.HashChainService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 自定义 KPI 服务（B12 Phase4 / D1-8 §七）。
 *
 * formula DSL：{@code {terms:{a:{agg,field,filters}}, expr, decimals}}。每个 term 是一个"带筛选的标量聚合"
 * （COUNT/SUM/AVG/MIN/MAX），expr 用 {@link ArithmeticEvaluator} 安全求值（仅加减乘除与括号、项标识、数字）。
 *
 * 强制红线：
 *  1) term 的聚合函数/字段/筛选一律经 {@link ObjectColumnCatalog} 白名单——与视图/报表共享同一「统一数据访问层」，
 *     未授权字段拒；
 *  2) expr 走安全递归下降求值，绝不 eval/反射——杜绝表达式注入；除零 → 值判空；
 *  3) 每个 term 的标量聚合运行在已注入 visible_orgs 的 RLS 会话（宿主表兜底）——KPI 数据集无路径越过 visibleOrgs；
 *  4) **口径一致性**：一切字段访问经统一列白名单目录。当风险对象接入目录后，其风险等级/风险域列取自平台
 *     canonical schema（levelMatrix / risk_domain 字典），KPI 只能引用字典支撑列，**不能自定义阈值**。
 */
@Service
public class KpiDefService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @PersistenceContext
    private EntityManager em;

    private final KpiDefRepository repository;
    private final ObjectColumnCatalog catalog;
    private final HashChainService hashChainService;

    public KpiDefService(KpiDefRepository repository, ObjectColumnCatalog catalog,
                         HashChainService hashChainService) {
        this.repository = repository;
        this.catalog = catalog;
        this.hashChainService = hashChainService;
    }

    /** KPI 求值结果（value 为 null 表示不可计算：除零/无数据/非有限）。 */
    public record KpiResult(Long id, String name, Double value, String unit) {
    }

    @Transactional(readOnly = true)
    public List<KpiDef> list(String objectType) {
        return repository.findByObjectTypeOrderByIdAsc(objectType);
    }

    /** 登记 KPI（先校验 term 白名单 + expr 语法，坏定义写入即拒）。 */
    @Transactional
    public KpiDef create(Long orgId, String objectType, String name, String formula, String unit, String actor) {
        validateFormula(objectType, formula);   // 写入即验白名单 + 表达式语法
        KpiDef k = new KpiDef(orgId, objectType, name, formula, unit, actor);
        KpiDef saved = repository.save(k);
        hashChainService.append(orgId, "CUSTOM_KPI_CREATE", actor, "CUSTOM_KPI:" + saved.getId(),
                "登记自定义 KPI " + objectType + " name=" + name);
        return saved;
    }

    @Transactional
    public KpiDef retire(Long id, String actor) {
        KpiDef k = get(id);
        k.setStatus("RETIRED");
        KpiDef saved = repository.save(k);
        hashChainService.append(k.getOrgId(), "CUSTOM_KPI_RETIRE", actor, "CUSTOM_KPI:" + id, "停用自定义 KPI");
        return saved;
    }

    /** 求值已登记 KPI（按 id）。 */
    @Transactional(readOnly = true)
    public KpiResult evaluate(Long id) {
        KpiDef k = get(id);
        Double v = compute(k.getObjectType(), k.getFormula());
        return new KpiResult(k.getId(), k.getName(), v, k.getUnit());
    }

    /** 预览临时公式（不落库，供构建器即时查看）。 */
    @Transactional(readOnly = true)
    public KpiResult preview(String objectType, String name, String formula, String unit) {
        Double v = compute(objectType, formula);
        return new KpiResult(null, name, v, unit);
    }

    // ---------- 求值引擎 ----------

    /** 仅校验（写入时）：解析 terms 白名单编译 + expr 语法（用占位值代入）。 */
    private void validateFormula(String objectType, String formula) {
        JsonNode def = readFormula(formula);
        Map<String, ObjectColumnCatalog.Col> allowed = catalog.allowedColumns(objectType);
        if (catalog.hostTable(objectType) == null) {
            throw new IllegalArgumentException("不支持的对象类型：" + objectType);
        }
        JsonNode terms = def.path("terms");
        if (!terms.isObject() || terms.isEmpty()) {
            throw new IllegalArgumentException("KPI 至少需要一个 term");
        }
        Map<String, Double> dummy = new LinkedHashMap<>();
        for (Iterator<String> it = terms.fieldNames(); it.hasNext(); ) {
            String key = it.next();
            compileTerm(objectType, allowed, terms.get(key));   // 触发白名单/聚合枚举校验
            dummy.put(key, 1.0);
        }
        // 用占位值代入，校验 expr 语法与标识引用（只引用已定义 term）
        ArithmeticEvaluator.eval(def.path("expr").asText(""), dummy);
    }

    /** 计算 KPI 值：逐 term 跑标量聚合（RLS 裁剪），再安全求值 expr。非有限/除零 → null。 */
    private Double compute(String objectType, String formula) {
        JsonNode def = readFormula(formula);
        Map<String, ObjectColumnCatalog.Col> allowed = catalog.allowedColumns(objectType);
        JsonNode terms = def.path("terms");
        Map<String, Double> vars = new LinkedHashMap<>();
        for (Iterator<String> it = terms.fieldNames(); it.hasNext(); ) {
            String key = it.next();
            vars.put(key, evalTerm(objectType, allowed, terms.get(key)));
        }
        double v = ArithmeticEvaluator.eval(def.path("expr").asText(""), vars);
        int decimals = def.path("decimals").asInt(2);
        if (!Double.isFinite(v)) {
            return null;   // 除零/无数据 → 前端显示"—"
        }
        double scale = Math.pow(10, Math.max(0, Math.min(6, decimals)));
        return Math.round(v * scale) / scale;
    }

    /** 编译单个 term 为 (聚合 SQL, 参数化 WHERE)，仅做白名单/聚合枚举校验（不执行）。 */
    private CompiledTerm compileTerm(String objectType, Map<String, ObjectColumnCatalog.Col> allowed, JsonNode term) {
        String agg = term.path("agg").asText("count");
        String field = term.path("field").asText("");
        String aggSql = catalog.aggregateSql(allowed, agg, field);
        StringBuilder where = new StringBuilder();
        Map<String, Object> params = new LinkedHashMap<>();
        int pi = 0;
        if (term.has("filters") && term.get("filters").isArray()) {
            for (JsonNode f : term.get("filters")) {
                String fld = f.path("field").asText("");
                String op = f.path("op").asText("eq");
                ObjectColumnCatalog.Col c = allowed.get(fld);
                if (c == null) {
                    throw new IllegalArgumentException("KPI 筛选引用了未授权字段：" + fld);
                }
                String pname = "p" + (pi++);
                where.append(where.length() == 0 ? " WHERE " : " AND ")
                        .append(catalog.buildCondition(c, op, pname, f.path("value"), params));
            }
        }
        return new CompiledTerm(aggSql, where.toString(), params);
    }

    private record CompiledTerm(String aggSql, String where, Map<String, Object> params) {
    }

    /** 执行单个 term 的标量聚合，返回数值（null → 0.0，供算术）。RLS 裁剪。 */
    private double evalTerm(String objectType, Map<String, ObjectColumnCatalog.Col> allowed, JsonNode term) {
        CompiledTerm ct = compileTerm(objectType, allowed, term);
        String table = catalog.hostTable(objectType);
        String sql = "SELECT " + ct.aggSql() + " FROM " + table + ct.where();
        Query q = em.createNativeQuery(sql);
        ct.params().forEach(q::setParameter);
        Object r = q.getSingleResult();
        return r == null ? 0.0 : ((Number) r).doubleValue();
    }

    private JsonNode readFormula(String formula) {
        try {
            return MAPPER.readTree(formula == null || formula.isBlank() ? "{}" : formula);
        } catch (com.fasterxml.jackson.core.JacksonException e) {
            throw new IllegalArgumentException("KPI 公式不是合法 JSON：" + e.getOriginalMessage());
        }
    }

    private KpiDef get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("自定义 KPI 不存在或不可见：id=" + id));
    }
}
