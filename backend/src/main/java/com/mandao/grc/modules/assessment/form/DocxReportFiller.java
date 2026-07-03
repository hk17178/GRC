package com.mandao.grc.modules.assessment.form;

import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTbl;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 评估报告回填器（表单引擎 P3）：把填写值灌回上传的官方 .docx 模板，产出"格式与官方模板一致"的报告 docx。
 *
 * 与 {@link DocxFormParser} 同一套占位符约定（方案 A）：
 *  - 标量 {@code ${字段|类型}} → 填入格式化后的值（level→中文档位，其余原样）；
 *  - 明细表：定位 {@code ${#列表}} 标记后的表格，按填写行数克隆"占位符模板行"并逐行回填，再清标记；
 *  - 占位符可能被 Word 拆到多个 run，回填时按段落整体文本替换后写回首个 run（保证跨 run 命中）。
 *
 * 产物再经 {@link ReportPdfService} 转 PDF。
 */
@Component
public class DocxReportFiller {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}|]+)(?:\\|([^}]+))?\\}");

    private static final Map<String, String> LEVEL_LABEL = Map.of(
            "VERY_LOW", "极低", "LOW", "低", "MID", "中", "HIGH", "高", "VERY_HIGH", "极高");

    /**
     * 回填。
     *
     * @param templateDocx 模板 .docx 原始字节（含占位符）
     * @param schema       该模板解析出的表单结构（用于识别字段类型/明细表）
     * @param answers      填写值（标量 + 明细表行数组）
     * @return 回填后的 docx 字节
     */
    @SuppressWarnings("unchecked")
    public byte[] fill(byte[] templateDocx, FormSchema schema, Map<String, Object> answers) {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(templateDocx))) {
            Map<String, Object> ans = answers == null ? Map.of() : answers;

            // 字段类型表（标量），用于按 level 等类型格式化
            Map<String, String> scalarTypes = new HashMap<>();
            for (FormSchema.Section sec : schema.sections()) {
                for (FormSchema.Field f : sec.fields()) {
                    scalarTypes.put(f.key(), f.type());
                }
            }

            // ---- Pass 1：标量回填（只替换已知标量字段；明细表列占位符/标记留待 Pass 2）----
            Function<String, String> scalarResolver = key -> {
                if (key.startsWith("#")) {
                    return null; // 明细表标记，Pass 2 处理
                }
                if (!scalarTypes.containsKey(key)) {
                    // 非 schema 字段：若填写值里有同名标量（如 V46 背景建立保留占位符 ${评估范围} 等），
                    // 按文本回填；值为数组（明细表数据）则仍留给 Pass 2。
                    Object v = ans.get(key);
                    if (v != null && !(v instanceof List)) {
                        return format(v, "text");
                    }
                    return null;
                }
                return format(ans.get(key), scalarTypes.get(key));
            };
            for (XWPFParagraph p : doc.getParagraphs()) {
                replaceInParagraph(p, scalarResolver);
            }
            for (XWPFTable t : doc.getTables()) {
                for (XWPFTableRow row : t.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        for (XWPFParagraph p : cell.getParagraphs()) {
                            replaceInParagraph(p, scalarResolver);
                        }
                    }
                }
            }

            // ---- Pass 2：明细表回填（按 body 顺序定位 ${#列表} 标记 → 紧随表格 → 克隆行）----
            fillLists(doc, schema, ans);

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            doc.write(bos);
            return bos.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("回填报告失败：" + e.getMessage(), e);
        }
    }

    /** 明细表回填：遍历正文元素，遇 ${#列表} 标记则把其后表格按填写行克隆回填。 */
    @SuppressWarnings("unchecked")
    private void fillLists(XWPFDocument doc, FormSchema schema, Map<String, Object> answers) {
        // 列类型表：listKey → (colKey → type)
        Map<String, Map<String, String>> listColTypes = new HashMap<>();
        Map<String, String> listLabelToKey = new HashMap<>();
        for (FormSchema.Section sec : schema.sections()) {
            for (FormSchema.ListBlock list : sec.lists()) {
                Map<String, String> cols = new HashMap<>();
                for (FormSchema.Field c : list.columns()) {
                    cols.put(c.key(), c.type());
                }
                listColTypes.put(list.key(), cols);
            }
        }

        List<IBodyElement> els = doc.getBodyElements();
        for (int i = 0; i < els.size(); i++) {
            IBodyElement el = els.get(i);
            if (!(el instanceof XWPFParagraph marker)) {
                continue;
            }
            String text = marker.getText();
            if (text == null || !text.contains("${#")) {
                continue;
            }
            Matcher m = Pattern.compile("\\$\\{#([^}|]+)").matcher(text);
            if (!m.find()) {
                continue;
            }
            String listKey = m.group(1).trim();
            // 找紧随的表格
            XWPFTable table = null;
            for (int j = i + 1; j < els.size(); j++) {
                if (els.get(j) instanceof XWPFTable t) {
                    table = t;
                    break;
                }
                if (els.get(j) instanceof XWPFParagraph pj && pj.getText() != null && !pj.getText().isBlank()) {
                    break; // 中间隔了非空段落，放弃匹配
                }
            }
            // 清掉标记文本
            replaceInParagraph(marker, k -> "");
            if (table == null) {
                continue;
            }
            Object rowsObj = answers.get(listKey);
            List<Map<String, Object>> dataRows = new ArrayList<>();
            if (rowsObj instanceof List<?> rows) {
                for (Object r : rows) {
                    if (r instanceof Map<?, ?> mm) {
                        dataRows.add((Map<String, Object>) mm);
                    }
                }
            }
            cloneAndFillRows(table, listColTypes.getOrDefault(listKey, Map.of()), dataRows);
        }
    }

    /** 找到含占位符的"模板行"，按 dataRows 克隆回填，最后删模板行。无数据则保留一空行（删占位符）。 */
    private void cloneAndFillRows(XWPFTable table, Map<String, String> colTypes, List<Map<String, Object>> dataRows) {
        CTTbl ctTbl = table.getCTTbl();
        // 模板行：首个含占位符的行
        int tplPos = -1;
        XWPFTableRow tplRow = null;
        for (int r = 0; r < table.getRows().size(); r++) {
            XWPFTableRow row = table.getRows().get(r);
            boolean hasPh = row.getTableCells().stream()
                    .anyMatch(c -> c.getText() != null && c.getText().contains("${"));
            if (hasPh) {
                tplPos = r;
                tplRow = row;
                break;
            }
        }
        if (tplRow == null) {
            return;
        }

        if (dataRows.isEmpty()) {
            // 无数据：把模板行占位符清空，保留一空行
            for (XWPFTableCell cell : tplRow.getTableCells()) {
                for (XWPFParagraph p : cell.getParagraphs()) {
                    replaceInParagraph(p, k -> "");
                }
            }
            return;
        }

        // 逐行克隆填充（操作底层 CTTbl，规避 POI 行模型缓存不同步）
        List<CTRow> filled = new ArrayList<>();
        for (Map<String, Object> rowData : dataRows) {
            CTRow ct = (CTRow) tplRow.getCtRow().copy();
            XWPFTableRow tmp = new XWPFTableRow(ct, table);
            for (XWPFTableCell cell : tmp.getTableCells()) {
                for (XWPFParagraph p : cell.getParagraphs()) {
                    replaceInParagraph(p, key -> {
                        if (!rowData.containsKey(key) && !colTypes.containsKey(key)) {
                            return ""; // 未知列占位符也清空，避免残留
                        }
                        return format(rowData.get(key), colTypes.get(key));
                    });
                }
            }
            filled.add(tmp.getCtRow());
        }
        // 插入到模板行之后，再删模板行
        for (int k = 0; k < filled.size(); k++) {
            ctTbl.insertNewTr(tplPos + 1 + k);
            ctTbl.setTrArray(tplPos + 1 + k, filled.get(k));
        }
        ctTbl.removeTr(tplPos);
    }

    /** 段落级占位符替换：按整段文本替换（跨 run 命中），结果写回首个 run、清其余 run。resolver 返回 null 表示保留。 */
    private void replaceInParagraph(XWPFParagraph p, Function<String, String> resolver) {
        String text = p.getText();
        if (text == null || !text.contains("${")) {
            return;
        }
        Matcher m = PLACEHOLDER.matcher(text);
        StringBuffer sb = new StringBuffer();
        boolean changed = false;
        while (m.find()) {
            String key = m.group(1).trim();
            String rep = resolver.apply(key);
            if (rep == null) {
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
            } else {
                changed = true;
                m.appendReplacement(sb, Matcher.quoteReplacement(rep));
            }
        }
        m.appendTail(sb);
        if (!changed) {
            return;
        }
        List<XWPFRun> runs = p.getRuns();
        if (runs.isEmpty()) {
            p.createRun().setText(sb.toString(), 0);
            return;
        }
        for (int i = runs.size() - 1; i >= 1; i--) {
            p.removeRun(i);
        }
        runs.get(0).setText(sb.toString(), 0);
    }

    /** 值格式化：level→中文档位；其余原样；null→空串。 */
    private String format(Object v, String type) {
        if (v == null) {
            return "";
        }
        String s = v.toString();
        if ("level".equals(type)) {
            return LEVEL_LABEL.getOrDefault(s, s);
        }
        return s;
    }
}
