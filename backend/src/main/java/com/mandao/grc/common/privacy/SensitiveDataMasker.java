package com.mandao.grc.common.privacy;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 敏感数据脱敏工具（合规扩展包 B29/B30）。
 *
 * 对文本中的中国常见个人敏感信息做保留头尾的掩码，用于：
 *  - B29 出站回复自动脱敏（对外发送前抹除 PII，红线三步之一）；
 *  - B30 跨子公司汇总脱敏（集团层看下属机构数据时不暴露明文 PII）。
 *
 * 规则（顺序敏感：先长后短，避免手机号被身份证规则误吞）：
 *  - 18 位身份证号 → 保留前 6 后 4，中间星号；
 *  - 11 位手机号   → 保留前 3 后 4；
 *  - 13~19 位银行卡号 → 保留后 4；
 *  - 邮箱          → 保留首字符与域名。
 * 纯工具，无状态、线程安全。
 */
public final class SensitiveDataMasker {

    // 用 (?<!\d)…(?!\d) 数字边界而非 \b：\b 在中文（CJK）与数字相邻处不可靠，
    // 数字负向环视能稳定框住"独立的 N 位数字串"，中英混排都成立。
    private static final Pattern ID_CARD = Pattern.compile("(?<!\\d)(\\d{6})\\d{8}([0-9Xx]{4})(?!\\d)");
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(1[3-9]\\d)\\d{4}(\\d{4})(?!\\d)");
    private static final Pattern BANK_CARD = Pattern.compile("(?<!\\d)\\d{9,15}(\\d{4})(?!\\d)");
    private static final Pattern EMAIL = Pattern.compile("(?<![A-Za-z0-9._%+-])([A-Za-z0-9])[A-Za-z0-9._%+-]*(@[A-Za-z0-9.-]+\\.[A-Za-z]{2,})");

    private SensitiveDataMasker() {
    }

    /** 对文本做全量脱敏；null 原样返回。 */
    public static String mask(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String s = text;
        s = replace(ID_CARD, s, m -> m.group(1) + "********" + m.group(2));
        s = replace(PHONE, s, m -> m.group(1) + "****" + m.group(2));
        s = replace(BANK_CARD, s, m -> "************" + m.group(1));
        s = replace(EMAIL, s, m -> m.group(1) + "***" + m.group(2));
        return s;
    }

    /** 是否含可识别的敏感信息（用于出站前告警/门控判断）。 */
    public static boolean containsSensitive(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        return ID_CARD.matcher(text).find() || PHONE.matcher(text).find()
                || BANK_CARD.matcher(text).find() || EMAIL.matcher(text).find();
    }

    private interface Repl {
        String apply(Matcher m);
    }

    private static String replace(Pattern p, String s, Repl repl) {
        Matcher m = p.matcher(s);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(repl.apply(m)));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
