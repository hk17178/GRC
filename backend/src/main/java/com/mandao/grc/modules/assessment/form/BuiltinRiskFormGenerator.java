package com.mandao.grc.modules.assessment.form;

import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * 内置标准风险评估表单生成器（UAT 四轮 #3/#6：内置模板开箱即用 + 报告章节规范化）。
 *
 * 按 GB/T 20984 / ISO 27005 报告结构生成完整 .docx 模板（占位符方案 A 约定），
 * 经既有 DocxFormParser 解析即得规范填写表单；报告导出=同一 docx 回填，格式即模板格式。
 *
 * 章节结构：
 *  一、评估概述（背景族保留占位符自动回填：范围/目的/依据/方法/准则/评估组/期间）
 *  二、资产识别（明细表）
 *  三、威胁与脆弱性识别（明细表）
 *  四、风险分析与评价（风险清单明细表：可能性×影响→固有，处置→残余）
 *  五、风险处置与建议
 *  六、评估结论（整体残余等级参与 CR-002 门控聚合——字段名含"残余"）
 */
@Component
public class BuiltinRiskFormGenerator {

    /** 生成内置标准表单 .docx 字节（每次生成内容一致，幂等）。 */
    public byte[] generate(String frameworkLabel) {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XWPFParagraph title = doc.createParagraph();
            title.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun tr = title.createRun();
            tr.setText(frameworkLabel + "风险评估报告");
            tr.setBold(true);
            tr.setFontSize(18);
            para(doc, "评估对象：${评估标题}　编制：${评估人}");

            heading(doc, "一、评估概述");
            para(doc, "1.1 评估目的与背景：${评估目的}");
            para(doc, "1.2 评估范围与边界：${评估范围}");
            para(doc, "1.3 依据标准：${依据标准}");
            para(doc, "1.4 方式方法：${评估方法}");
            para(doc, "1.5 评估准则：${评估准则}");
            para(doc, "1.6 评估组与周期：${评估组}（${评估期间}）");
            para(doc, "1.7 其他说明：${概述补充|textarea}");

            heading(doc, "二、资产识别");
            para(doc, "${#资产清单}");
            table(doc,
                    new String[]{"资产名称", "资产类型", "重要程度", "说明"},
                    new String[]{"${资产名称|text}", "${资产类型|text}", "${重要程度|level}", "${资产说明|text}"});

            heading(doc, "三、威胁与脆弱性识别");
            para(doc, "${#威胁脆弱性清单}");
            table(doc,
                    new String[]{"威胁", "脆弱性", "已有安全措施"},
                    new String[]{"${威胁|text}", "${脆弱性|text}", "${已有措施|text}"});

            heading(doc, "四、风险分析与评价");
            para(doc, "风险值 = 可能性 × 影响，按五级矩阵定级；处置后评价残余等级。");
            para(doc, "${#风险清单}");
            table(doc,
                    new String[]{"风险描述", "可能性", "影响", "固有等级", "处置措施", "残余等级"},
                    new String[]{"${风险描述|text}", "${可能性|score}", "${影响|score}",
                            "${固有等级|level}", "${处置措施|text}", "${残余等级|level}"});

            heading(doc, "五、风险处置与建议");
            para(doc, "${处置总体建议|textarea}");

            heading(doc, "六、评估结论");
            para(doc, "整体残余风险等级：${整体残余等级|level}");
            para(doc, "结论与后续安排：${评估结论|textarea}");

            doc.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("内置表单生成失败", e);
        }
    }

    private static void para(XWPFDocument doc, String text) {
        doc.createParagraph().createRun().setText(text);
    }

    /** 章节标题：styleID 置 "1" 命中解析器的大纲数字判定，同时手动加粗放大保证观感。 */
    private static void heading(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        p.setStyle("1");
        XWPFRun r = p.createRun();
        r.setText(text);
        r.setBold(true);
        r.setFontSize(14);
    }

    /** 明细表：表头行 + 占位符模板行（P1 解析约定）。 */
    private static void table(XWPFDocument doc, String[] headers, String[] templates) {
        XWPFTable t = doc.createTable();
        XWPFTableRow head = t.getRow(0);
        for (int i = 0; i < headers.length; i++) {
            (i == 0 ? head.getCell(0) : head.addNewTableCell()).setText(headers[i]);
        }
        XWPFTableRow tpl = t.createRow();
        for (int i = 0; i < templates.length && i < tpl.getTableCells().size(); i++) {
            tpl.getCell(i).setText(templates[i]);
        }
    }
}
