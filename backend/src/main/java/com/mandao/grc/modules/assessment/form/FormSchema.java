package com.mandao.grc.modules.assessment.form;

import java.util.List;

/**
 * 表单结构（D1-6 结构配置层的 P1 落地形态）。
 *
 * 由 {@link DocxFormParser} 从上传的 .docx 占位符解析得到，以 JSON 文本持久化在
 * template_form.schema_json；运行期前端据此渲染分章节的可填写表单，填写值按字段 key 存
 * assessment_answer.answers_json。
 *
 * 结构：文档 → 若干章节(section，来自 Word 标题)；每章节含
 *   - 标量字段 field（来自段落/布局表格里的 {@code ${字段}}）
 *   - 明细表 list（来自带 {@code ${#列表}} 标记的重复行表格）。
 */
public record FormSchema(List<Section> sections) {

    /** 一个章节（对应 Word 的一个标题分组）。 */
    public record Section(String title, List<Field> fields, List<ListBlock> lists) {
    }

    /**
     * 标量字段。
     *
     * @param key     字段键（= 占位符名，answers 以此为键）
     * @param label   显示标签（默认同 key）
     * @param type    控件类型：text/textarea/date/number/select/score/level
     * @param options 当 type=select 时的选项；其余为 null
     */
    public record Field(String key, String label, String type, List<String> options) {
    }

    /**
     * 明细表（可增删行的列表，如"风险点清单"）。
     *
     * @param key     列表键（= {@code ${#列表}} 中的名字）
     * @param label   列表标题
     * @param columns 列定义（每列是一个 {@link Field}，type 决定单元格控件）
     */
    public record ListBlock(String key, String label, List<Field> columns) {
    }
}
