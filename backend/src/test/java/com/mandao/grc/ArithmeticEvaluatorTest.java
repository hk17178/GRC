package com.mandao.grc;

import com.mandao.grc.modules.custom.ArithmeticEvaluator;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * KPI 公式安全求值器单元测试（B12 Phase4 / D1-8 §七）。
 * 验证：四则运算与优先级、括号、一元负号、除零→NaN、未定义标识/非法字符抛异常（防注入）。
 */
class ArithmeticEvaluatorTest {

    private static final Map<String, Double> V = Map.of("a", 2.0, "b", 4.0, "c", 10.0);

    @Test
    void 四则运算与优先级() {
        assertEquals(50.0, ArithmeticEvaluator.eval("a / b * 100", V), 1e-9);   // 2/4*100
        assertEquals(22.0, ArithmeticEvaluator.eval("a + b * c / 2", V), 1e-9);  // 2 + 4*10/2 = 22
        assertEquals(60.0, ArithmeticEvaluator.eval("(a + b) * c", V), 1e-9);    // (2+4)*10
        assertEquals(-8.0, ArithmeticEvaluator.eval("a - c", V), 1e-9);
    }

    @Test
    void 一元负号与小数字面量() {
        assertEquals(-2.0, ArithmeticEvaluator.eval("-a", V), 1e-9);
        assertEquals(3.5, ArithmeticEvaluator.eval("1.5 + a", V), 1e-9);
    }

    @Test
    void 除零返回NaN() {
        assertTrue(Double.isNaN(ArithmeticEvaluator.eval("a / 0", V)));
        assertTrue(Double.isNaN(ArithmeticEvaluator.eval("a / (b - b)", V)));
    }

    @Test
    void 未定义标识与非法字符抛异常防注入() {
        assertThrows(IllegalArgumentException.class, () -> ArithmeticEvaluator.eval("a + z", V));
        assertThrows(IllegalArgumentException.class, () -> ArithmeticEvaluator.eval("a; DROP TABLE", V));
        assertThrows(IllegalArgumentException.class, () -> ArithmeticEvaluator.eval("a @ b", V));
        assertThrows(IllegalArgumentException.class, () -> ArithmeticEvaluator.eval("(a + b", V));   // 括号不匹配
        assertThrows(IllegalArgumentException.class, () -> ArithmeticEvaluator.eval("", V));         // 空表达式
    }
}
