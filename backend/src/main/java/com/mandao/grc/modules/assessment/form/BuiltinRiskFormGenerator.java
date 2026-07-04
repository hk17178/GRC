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

    /**
     * 按模板体系差异化生成（八轮 8-8 / 评估报告 A4/C5 第一批）：
     * 等保(TPL-MLPS)/人行支付(TPL-PBOC) 有专属方法论章节；其余体系暂用通用骨架
     * （模板卡带「通用骨架」提示，正式使用可上传行业表单，分批差异化继续推进）。
     */
    public byte[] generateFor(String templateCode, String frameworkLabel) {
        if ("TPL-MLPS".equals(templateCode)) {
            return generateMlps();
        }
        if ("TPL-PBOC".equals(templateCode)) {
            return generatePboc();
        }
        return generate(frameworkLabel);
    }

    /**
     * 等保 2.0（三级）专属表单：通用骨架的 资产/ATV/风险清单 保留（预填链路不变），
     * 增设「等保符合性自查」章节——按 GB/T 22239 八大类逐控制域打分 + 差距说明。
     */
    private byte[] generateMlps() {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XWPFParagraph title = doc.createParagraph();
            title.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun tr = title.createRun();
            tr.setText("网络安全等级保护（三级）风险评估与符合性自查报告");
            tr.setBold(true);
            tr.setFontSize(18);
            para(doc, "评估对象：${评估标题}　编制：${评估人}");

            heading(doc, "一、评估概述");
            para(doc, "1.1 评估目的与背景：${评估目的}");
            para(doc, "1.2 评估范围与边界（定级对象）：${评估范围}");
            para(doc, "1.3 依据标准：${依据标准}");
            para(doc, "1.4 方式方法：${评估方法}");
            para(doc, "1.5 评估准则：${评估准则}");
            para(doc, "1.6 评估组与周期：${评估组}（${评估期间}）");

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

            heading(doc, "四、等保符合性自查（技术要求）");
            para(doc, "打分口径：0=未建设 1=严重不足 2=部分落实 3=基本落实 4=较好 5=完全符合");
            mlpsDomain(doc, "4.1 安全物理环境", new String[]{
                    "机房位置选择与物理访问控制", "防盗窃防破坏与防雷击", "防火防水防潮", "电力供应与电磁防护"});
            mlpsDomain(doc, "4.2 安全通信网络", new String[]{
                    "网络架构与区域划分", "通信传输加密", "可信验证"});
            mlpsDomain(doc, "4.3 安全区域边界", new String[]{
                    "边界防护与访问控制", "入侵防范", "恶意代码与垃圾邮件防范", "安全审计（边界）"});
            mlpsDomain(doc, "4.4 安全计算环境", new String[]{
                    "身份鉴别", "访问控制（最小权限）", "安全审计（主机/应用）", "入侵防范与恶意代码（主机）",
                    "数据完整性与保密性", "数据备份恢复", "剩余信息保护与个人信息保护"});
            mlpsDomain(doc, "4.5 安全管理中心", new String[]{
                    "系统管理与审计管理", "安全管理与集中管控"});

            heading(doc, "五、等保符合性自查（管理要求）");
            mlpsDomain(doc, "5.1 安全管理制度", new String[]{
                    "安全策略与管理制度体系", "制定发布与评审修订"});
            mlpsDomain(doc, "5.2 安全管理机构与人员", new String[]{
                    "岗位设置与人员配备", "授权审批与沟通合作", "人员录用离岗与安全意识培训"});
            mlpsDomain(doc, "5.3 安全建设管理", new String[]{
                    "定级备案与方案设计", "产品采购与自行/外包开发", "测试验收与系统交付", "等级测评与服务商选择"});
            mlpsDomain(doc, "5.4 安全运维管理", new String[]{
                    "环境/资产/介质/设备管理", "漏洞与风险管理", "网络与系统安全管理",
                    "恶意代码防范与配置管理", "密码/变更/备份恢复管理", "安全事件处置与应急预案", "外包运维管理"});

            heading(doc, "六、风险分析与评价");
            para(doc, "风险值 = 可能性 × 影响，按五级矩阵定级；处置后评价残余等级。");
            para(doc, "${#风险清单}");
            table(doc,
                    new String[]{"风险描述", "可能性", "影响", "固有等级", "处置措施", "残余等级"},
                    new String[]{"${风险描述|text}", "${可能性|score}", "${影响|score}",
                            "${固有等级|level}", "${处置措施|text}", "${残余等级|level}"});

            heading(doc, "七、整改建议与结论");
            para(doc, "主要差距与整改建议：${整改建议|textarea}");
            para(doc, "整体残余风险等级：${整体残余等级|level}");
            para(doc, "结论与后续安排（含测评计划）：${评估结论|textarea}");

            doc.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("等保内置表单生成失败", e);
        }
    }

    /** 等保控制域：每项一行 打分+差距说明。 */
    private static void mlpsDomain(XWPFDocument doc, String domainTitle, String[] items) {
        para(doc, domainTitle);
        for (String item : items) {
            para(doc, "・" + item + "：${" + domainTitle.substring(domainTitle.indexOf(' ') + 1)
                    + "-" + item + "|score}　差距：${" + domainTitle.substring(domainTitle.indexOf(' ') + 1)
                    + "-" + item + "-差距|text}");
        }
    }

    /**
     * 人行支付监管专属表单：按支付机构监管重点分章自查（非银行支付机构条例/备付金/反洗钱配合等）。
     */
    private byte[] generatePboc() {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XWPFParagraph title = doc.createParagraph();
            title.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun tr = title.createRun();
            tr.setText("支付业务监管合规自查报告（人行口径）");
            tr.setBold(true);
            tr.setFontSize(18);
            para(doc, "评估对象：${评估标题}　编制：${评估人}");

            heading(doc, "一、自查概述");
            para(doc, "1.1 自查目的与背景：${评估目的}");
            para(doc, "1.2 自查范围（业务类型/系统）：${评估范围}");
            para(doc, "1.3 依据（条例/办法/规范）：${依据标准}");
            para(doc, "1.4 方式方法：${评估方法}");
            para(doc, "1.5 自查组与周期：${评估组}（${评估期间}）");

            heading(doc, "二、重点领域自查");
            pbocArea(doc, "2.1 备付金管理", new String[]{
                    "备付金集中存管与专户管理", "备付金划转与核对机制", "客户备付金信息披露"});
            pbocArea(doc, "2.2 商户与受理终端管理", new String[]{
                    "商户实名制与准入审核", "商户风险评级与巡检", "受理终端（含条码）注册与移机监控"});
            pbocArea(doc, "2.3 交易与清算合规", new String[]{
                    "交易真实性与限额管理", "清算路径合规（断直连口径）", "交易日志留存年限（5-10年）"});
            pbocArea(doc, "2.4 消费者权益保护", new String[]{
                    "收费公示与协议规范", "投诉处理时效与渠道", "个人金融信息保护"});
            pbocArea(doc, "2.5 系统与数据安全", new String[]{
                    "支付系统等保与灾备", "敏感支付数据加密与脱敏", "变更与应急演练"});
            pbocArea(doc, "2.6 反洗钱配合义务", new String[]{
                    "客户身份识别（KYC）落实", "大额与可疑交易监测报送", "名单监控与冻结配合"});

            heading(doc, "三、资产识别");
            para(doc, "${#资产清单}");
            table(doc,
                    new String[]{"资产名称", "资产类型", "重要程度", "说明"},
                    new String[]{"${资产名称|text}", "${资产类型|text}", "${重要程度|level}", "${资产说明|text}"});

            heading(doc, "四、威胁与脆弱性识别");
            para(doc, "${#威胁脆弱性清单}");
            table(doc,
                    new String[]{"威胁", "脆弱性", "已有安全措施"},
                    new String[]{"${威胁|text}", "${脆弱性|text}", "${已有措施|text}"});

            heading(doc, "五、风险分析与评价");
            para(doc, "${#风险清单}");
            table(doc,
                    new String[]{"风险描述", "可能性", "影响", "固有等级", "处置措施", "残余等级"},
                    new String[]{"${风险描述|text}", "${可能性|score}", "${影响|score}",
                            "${固有等级|level}", "${处置措施|text}", "${残余等级|level}"});

            heading(doc, "六、整改与结论");
            para(doc, "问题清单与整改安排：${整改建议|textarea}");
            para(doc, "整体残余风险等级：${整体残余等级|level}");
            para(doc, "自查结论：${评估结论|textarea}");

            doc.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("人行支付内置表单生成失败", e);
        }
    }

    /** 支付重点领域：每项一行 打分+说明。 */
    private static void pbocArea(XWPFDocument doc, String areaTitle, String[] items) {
        para(doc, areaTitle);
        String key = areaTitle.substring(areaTitle.indexOf(' ') + 1);
        for (String item : items) {
            para(doc, "・" + item + "：${" + key + "-" + item + "|score}　说明：${"
                    + key + "-" + item + "-说明|text}");
        }
    }

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
