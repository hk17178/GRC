package com.mandao.grc.modules.assessment.form;

import com.mandao.grc.modules.assessment.RiskLevel;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 风险评估打分/聚合服务（表单引擎 P2 · D1-6 打分映射层落地起步）。
 *
 * 两件事：
 *  1) 五级映射 levelMatrix（默认定量口径，需求 4.2.2 / D1-6）：风险值 = 可能性 × 影响（各 1~5，积 1~25）
 *     → 极低/低/中/高/极高（VERY_LOW/LOW/MID/HIGH/VERY_HIGH）。模板可改阈值，级数固定五级。
 *  2) 整体残余等级聚合：扫描表单填写里"残余风险"档（明细表 level 列 + 顶层 整体/残余 level 字段），
 *     取最高一档作为评估整体等级——驱动看板/任务列表的真实风险等级与 CR-002 完成门控。
 */
@Service
public class ScoringService {

    /** 默认五级区间（D1-6 示例）：风险值上界 → 等级。 */
    private record Band(int max, RiskLevel level) {
    }

    private static final List<Band> LEVEL_MATRIX = List.of(
            new Band(3, RiskLevel.VERY_LOW),
            new Band(6, RiskLevel.LOW),
            new Band(12, RiskLevel.MID),
            new Band(20, RiskLevel.HIGH),
            new Band(25, RiskLevel.VERY_HIGH));

    /** 可能性 × 影响 → 五级。入参越界自动夹到 [1,5]。 */
    public RiskLevel levelOf(int likelihood, int impact) {
        int l = clamp(likelihood);
        int i = clamp(impact);
        int score = l * i;
        for (Band b : LEVEL_MATRIX) {
            if (score <= b.max()) {
                return b.level();
            }
        }
        return RiskLevel.VERY_HIGH;
    }

    private int clamp(int v) {
        return Math.max(1, Math.min(5, v));
    }

    /**
     * 从表单填写聚合整体残余等级。
     *
     * 取以下两类"残余"档的最高一档：
     *  - 明细表中 type=level 且列标签/键含"残余"的单元格值；
     *  - 顶层 type=level 且字段标签/键含"整体"或"残余"的字段值。
     *
     * @param schema  表单结构
     * @param answers 填写值（标量 + 明细表数组）
     * @return 整体残余等级；无可判定时返回 null
     */
    @SuppressWarnings("unchecked")
    public RiskLevel overallResidual(FormSchema schema, Map<String, Object> answers) {
        if (schema == null || answers == null) {
            return null;
        }
        RiskLevel max = null;
        for (FormSchema.Section sec : schema.sections()) {
            // 顶层 level 字段（整体/残余）
            for (FormSchema.Field f : sec.fields()) {
                if ("level".equals(f.type()) && nameHints(f.key(), f.label(), "整体", "残余")) {
                    max = higher(max, parseLevel(answers.get(f.key())));
                }
            }
            // 明细表 level 列（残余）
            for (FormSchema.ListBlock list : sec.lists()) {
                List<String> residualCols = list.columns().stream()
                        .filter(c -> "level".equals(c.type()) && nameHints(c.key(), c.label(), "残余"))
                        .map(FormSchema.Field::key)
                        .toList();
                if (residualCols.isEmpty()) {
                    continue;
                }
                Object rowsObj = answers.get(list.key());
                if (rowsObj instanceof List<?> rows) {
                    for (Object rowObj : rows) {
                        if (rowObj instanceof Map<?, ?> row) {
                            for (String col : residualCols) {
                                max = higher(max, parseLevel(((Map<String, Object>) row).get(col)));
                            }
                        }
                    }
                }
            }
        }
        return max;
    }

    /** 标签或键包含任一关键字。 */
    private boolean nameHints(String key, String label, String... hints) {
        String k = (key == null ? "" : key) + "|" + (label == null ? "" : label);
        for (String h : hints) {
            if (k.contains(h)) {
                return true;
            }
        }
        return false;
    }

    /** 取较高一档（按枚举顺序 VERY_LOW<...<VERY_HIGH）。 */
    private RiskLevel higher(RiskLevel a, RiskLevel b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.ordinal() >= b.ordinal() ? a : b;
    }

    /** 把填写值解析为 RiskLevel（容错：非法/空返回 null）。 */
    private RiskLevel parseLevel(Object v) {
        if (v == null) {
            return null;
        }
        try {
            return RiskLevel.valueOf(v.toString().trim());
        } catch (Exception e) {
            return null;
        }
    }
}
