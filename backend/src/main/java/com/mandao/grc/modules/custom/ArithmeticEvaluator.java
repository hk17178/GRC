package com.mandao.grc.modules.custom;

import java.util.Map;

/**
 * 极简安全算术求值器（B12 Phase4 / D1-8 §七 KPI 公式 DSL 的表达式引擎）。
 *
 * 仅支持：数字字面量、标识符（映射到已算好的项值 Map）、二元 + - * /、括号、一元负号。
 * 递归下降解析，**绝不 eval/反射/方法调用**——杜绝表达式注入；未知标识符/非法字符即抛。
 * 除以 0 返回 {@link Double#NaN}（调用方据此判空，避免 KPI 崩）。
 *
 * 文法：
 *   expr   := term   (('+'|'-') term)*
 *   term   := factor (('*'|'/') factor)*
 *   factor := number | ident | '(' expr ')' | '-' factor
 */
public final class ArithmeticEvaluator {

    private final String src;
    private final Map<String, Double> vars;
    private int pos;

    private ArithmeticEvaluator(String src, Map<String, Double> vars) {
        this.src = src;
        this.vars = vars;
    }

    /** 求值 expr，标识符从 vars 取值。表达式非法（语法/未知标识/多余字符）抛 IllegalArgumentException。 */
    public static double eval(String expr, Map<String, Double> vars) {
        if (expr == null || expr.isBlank()) {
            throw new IllegalArgumentException("KPI 公式表达式为空");
        }
        ArithmeticEvaluator e = new ArithmeticEvaluator(expr, vars);
        double v = e.parseExpr();
        e.skipWs();
        if (e.pos != e.src.length()) {
            throw new IllegalArgumentException("KPI 公式含非法字符：位置 " + e.pos);
        }
        return v;
    }

    private double parseExpr() {
        double v = parseTerm();
        while (true) {
            skipWs();
            if (peek() == '+') { pos++; v += parseTerm(); }
            else if (peek() == '-') { pos++; v -= parseTerm(); }
            else { return v; }
        }
    }

    private double parseTerm() {
        double v = parseFactor();
        while (true) {
            skipWs();
            char c = peek();
            if (c == '*') { pos++; v *= parseFactor(); }
            else if (c == '/') {
                pos++;
                double d = parseFactor();
                v = d == 0 ? Double.NaN : v / d;   // 除零 → NaN（KPI 判空）
            } else {
                return v;
            }
        }
    }

    private double parseFactor() {
        skipWs();
        char c = peek();
        if (c == '(') {
            pos++;
            double v = parseExpr();
            skipWs();
            if (peek() != ')') {
                throw new IllegalArgumentException("KPI 公式括号不匹配");
            }
            pos++;
            return v;
        }
        if (c == '-') {
            pos++;
            return -parseFactor();
        }
        if (c == '+') {
            pos++;
            return parseFactor();
        }
        if (Character.isDigit(c) || c == '.') {
            return parseNumber();
        }
        if (Character.isLetter(c) || c == '_') {
            return parseIdent();
        }
        throw new IllegalArgumentException("KPI 公式含非法符号：'" + (c == '\0' ? "<结尾>" : c) + "'");
    }

    private double parseNumber() {
        int start = pos;
        while (pos < src.length() && (Character.isDigit(src.charAt(pos)) || src.charAt(pos) == '.')) {
            pos++;
        }
        try {
            return Double.parseDouble(src.substring(start, pos));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("KPI 公式数字非法：" + src.substring(start, pos));
        }
    }

    private double parseIdent() {
        int start = pos;
        while (pos < src.length()
                && (Character.isLetterOrDigit(src.charAt(pos)) || src.charAt(pos) == '_')) {
            pos++;
        }
        String id = src.substring(start, pos);
        Double v = vars.get(id);
        if (v == null) {
            throw new IllegalArgumentException("KPI 公式引用了未定义的项：" + id);
        }
        return v;
    }

    private void skipWs() {
        while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
            pos++;
        }
    }

    private char peek() {
        skipWs();
        return pos < src.length() ? src.charAt(pos) : '\0';
    }
}
