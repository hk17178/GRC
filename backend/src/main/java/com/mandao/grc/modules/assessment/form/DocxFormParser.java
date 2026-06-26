package com.mandao.grc.modules.assessment.form;

import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * .docx 表单解析器（占位符方案 A）。
 *
 * 约定（与 D1-6 P1 方案一致）：
 *  1) 标量字段：段落或布局表格单元格里写 {@code ${字段名}} 或 {@code ${字段名|类型}}；
 *     类型 ∈ text(默认)/textarea/date/number/score/level/select:选项1;选项2。
 *  2) 章节：Word 的标题样式段落（Heading/标题/数字大纲）自动成为一个章节分组。
 *  3) 明细表：在表格前一段写 {@code ${#列表名}} 标记，紧随的表格视为可增删行的明细表；
 *     表头行=列名，其下首个含占位符的行=列模板，每个 {@code ${列}} 占位符成为一列。
 *
 * 仅做结构解析，不改引擎：模板=数据。解析失败/无占位符则抛 {@link IllegalArgumentException}（→400）。
 */
@Component
public class DocxFormParser {

    /** 通用占位符：${名字} 或 ${名字|类型}。名字不含 '}' 与 '|'。 */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}|]+)(?:\\|([^}]+))?\\}");
    /** 明细表标记：${#列表名} 或 ${#列表名|标题}。 */
    private static final Pattern LIST_MARKER = Pattern.compile("\\$\\{#([^}|]+)(?:\\|([^}]+))?\\}");

    /**
     * 解析 .docx 字节流为表单结构。
     *
     * @param docxBytes 上传的 .docx 原始字节
     * @return 解析出的 {@link FormSchema}
     */
    public FormSchema parse(byte[] docxBytes) {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docxBytes))) {
            List<FormSchema.Section> sections = new ArrayList<>();
            FormSchema.Section current = newSection("评估信息");
            // 全局标量字段键去重（answers 以键索引，键必须唯一）
            Set<String> seenKeys = new HashSet<>();

            String pendingListKey = null;
            String pendingListLabel = null;

            for (IBodyElement el : doc.getBodyElements()) {
                if (el instanceof XWPFParagraph p) {
                    String text = p.getText() == null ? "" : p.getText().trim();
                    if (text.isEmpty()) {
                        continue;
                    }
                    // 明细表标记优先识别
                    Matcher mk = LIST_MARKER.matcher(text);
                    if (mk.find()) {
                        pendingListKey = mk.group(1).trim();
                        pendingListLabel = mk.group(2) != null ? mk.group(2).trim() : pendingListKey;
                        continue;
                    }
                    // 标题样式 → 起新章节
                    if (isHeading(p)) {
                        if (!isEmpty(current)) {
                            sections.add(current);
                        }
                        current = newSection(text);
                        continue;
                    }
                    // 段落内标量占位符 → 字段
                    addScalarFields(text, current, seenKeys);

                } else if (el instanceof XWPFTable t) {
                    if (pendingListKey != null) {
                        FormSchema.ListBlock lb = parseListTable(t, pendingListKey, pendingListLabel);
                        if (lb != null) {
                            current.lists().add(lb);
                        }
                        pendingListKey = null;
                        pendingListLabel = null;
                    } else {
                        // 无标记的布局表格：扫描各单元格里的标量占位符（中文表单常见"标签格+占位符格"）
                        for (XWPFTableRow row : t.getRows()) {
                            for (XWPFTableCell cell : row.getTableCells()) {
                                addScalarFields(cell.getText() == null ? "" : cell.getText(), current, seenKeys);
                            }
                        }
                    }
                }
            }
            if (!isEmpty(current)) {
                sections.add(current);
            }
            if (sections.isEmpty()) {
                throw new IllegalArgumentException("未在文档中发现任何占位符 ${...}，无法生成表单");
            }
            return new FormSchema(sections);
        } catch (IOException e) {
            throw new IllegalArgumentException("解析 .docx 失败：" + e.getMessage(), e);
        }
    }

    /** 从一段文本中抽取全部标量占位符，去重后加入当前章节。 */
    private void addScalarFields(String text, FormSchema.Section section, Set<String> seenKeys) {
        Matcher m = PLACEHOLDER.matcher(text);
        while (m.find()) {
            String name = m.group(1).trim();
            if (name.startsWith("#")) {
                // 明细表标记，非标量字段
                continue;
            }
            if (!seenKeys.add(name)) {
                // 同名字段已出现，跳过（约定占位符名全局唯一）
                continue;
            }
            section.fields().add(toField(name, name, m.group(2)));
        }
    }

    /** 把一张被标记的表格解析为明细表：表头=列名，首个含占位符的行=列模板。 */
    private FormSchema.ListBlock parseListTable(XWPFTable t, String key, String label) {
        List<XWPFTableRow> rows = t.getRows();
        if (rows.isEmpty()) {
            return null;
        }
        // 表头行（首行）单元格文本作为列标签
        List<String> headerLabels = new ArrayList<>();
        for (XWPFTableCell c : rows.get(0).getTableCells()) {
            headerLabels.add(c.getText() == null ? "" : c.getText().trim());
        }
        // 找首个含占位符的数据行
        List<FormSchema.Field> columns = new ArrayList<>();
        for (int r = 1; r < rows.size(); r++) {
            List<XWPFTableCell> cells = rows.get(r).getTableCells();
            boolean rowHasPlaceholder = false;
            for (int c = 0; c < cells.size(); c++) {
                String cellText = cells.get(c).getText() == null ? "" : cells.get(c).getText();
                Matcher m = PLACEHOLDER.matcher(cellText);
                if (m.find()) {
                    rowHasPlaceholder = true;
                    String name = m.group(1).trim();
                    String colLabel = c < headerLabels.size() && !headerLabels.get(c).isEmpty()
                            ? headerLabels.get(c) : name;
                    columns.add(toField(name, colLabel, m.group(2)));
                }
            }
            if (rowHasPlaceholder) {
                break;
            }
        }
        return columns.isEmpty() ? null : new FormSchema.ListBlock(key, label, columns);
    }

    /** 由占位符名 + 类型串构造字段定义。 */
    private FormSchema.Field toField(String key, String label, String typeSpec) {
        String type = "text";
        List<String> options = null;
        if (typeSpec != null && !typeSpec.isBlank()) {
            String spec = typeSpec.trim();
            if (spec.startsWith("select")) {
                type = "select";
                int idx = spec.indexOf(':');
                if (idx >= 0) {
                    options = new ArrayList<>();
                    for (String opt : spec.substring(idx + 1).split(";")) {
                        if (!opt.isBlank()) {
                            options.add(opt.trim());
                        }
                    }
                }
            } else {
                type = spec.toLowerCase();
            }
        }
        return new FormSchema.Field(key, label, type, options);
    }

    /** 标题判定：样式 ID 含 heading/标题，或为大纲数字（1~9）。 */
    private boolean isHeading(XWPFParagraph p) {
        String sid = p.getStyleID();
        if (sid == null) {
            return false;
        }
        String s = sid.toLowerCase();
        return s.contains("heading") || s.contains("标题") || s.matches("[1-9]");
    }

    private FormSchema.Section newSection(String title) {
        return new FormSchema.Section(title, new ArrayList<>(), new ArrayList<>());
    }

    private boolean isEmpty(FormSchema.Section s) {
        return s.fields().isEmpty() && s.lists().isEmpty();
    }

    /** 供测试/工具复用的选项分隔常量说明（select:a;b;c）。 */
    static List<String> splitOptions(String raw) {
        return Arrays.asList(raw.split(";"));
    }
}
