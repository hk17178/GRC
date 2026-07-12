package com.mandao.grc;

import com.mandao.grc.common.auth.JwtService;
import com.mandao.grc.common.auth.LoginRateLimiter;
import com.mandao.grc.common.net.IpEgressGuard;
import com.mandao.grc.modules.ai.ConfigCrypto;
import com.mandao.grc.modules.custom.ArithmeticEvaluator;
import com.mandao.grc.modules.workflow.BpmnCompiler;
import com.mandao.grc.modules.workflow.FlowGraph;
import com.mandao.grc.modules.workflow.FlowValidationException;
import com.mandao.grc.modules.workflow.NodeType;
import org.junit.jupiter.api.Test;

import java.util.List;

import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 安全加固单测（纯构造·无 Spring 上下文·无容器，秒级）。
 * 安全评审 H-9 / M-12：JWT 与配置主密钥缺失 / 为已知开发默认 / 过短 → 一律 fail-fast，拒绝以可预测密钥启动。
 */
class SecurityHardeningTest {

    @Test
    void jwt密钥_缺失或默认或过短_拒绝构造_足够强则通过() {
        assertThrows(IllegalStateException.class, () -> new JwtService("", 12), "空密钥应拒");
        assertThrows(IllegalStateException.class, () -> new JwtService("   ", 12), "空白密钥应拒");
        assertThrows(IllegalStateException.class,
                () -> new JwtService("dev-only-secret-change-me-please-32bytes!!", 12), "已知开发默认应拒");
        assertThrows(IllegalStateException.class, () -> new JwtService("short-key", 12), "过短(<32字节)应拒");
        assertDoesNotThrow(() -> new JwtService("this-is-a-strong-jwt-secret-with-more-than-32-bytes", 12),
                "足够长的强密钥应通过");
    }

    @Test
    void 配置主密钥_缺失或开发默认_拒绝构造_自定义则通过() {
        assertThrows(IllegalStateException.class, () -> new ConfigCrypto(""), "空主密钥应拒");
        assertThrows(IllegalStateException.class, () -> new ConfigCrypto("grc-dev-config-secret-change-me"),
                "开发默认主密钥应拒");
        assertDoesNotThrow(() -> new ConfigCrypto("a-strong-random-config-master-secret"),
                "自定义强主密钥应通过");
    }

    @Test
    void h7_bpmn节点标识含越界字符_拒绝编译() {
        // 恶意 key 企图越出 XML 属性注入 flowable:class 委托达成 RCE —— 应被 H-7 字符白名单在编译前拦下
        FlowGraph.FlowNode bad = new FlowGraph.FlowNode(
                "s\" flowable:class=\"com.evil.Rce", NodeType.START, "开始",
                null, null, null, null, null, null, null);
        FlowGraph g = new FlowGraph(List.of(bad), List.of());
        // 校验先于对 flow 的解引用，故可传 null flow
        assertThrows(FlowValidationException.class, () -> new BpmnCompiler().compile(null, g),
                "含引号/越界字符的节点标识应被拒绝编译");
    }

    @Test
    void ipEgressGuard_内网与保留段阻断_公网放行() throws Exception {
        for (String ip : new String[]{"127.0.0.1", "192.168.1.1", "10.0.0.1", "172.16.0.1",
                "169.254.169.254", "100.64.0.1", "198.18.0.1", "fd00::1"}) {
            assertTrue(IpEgressGuard.isBlocked(InetAddress.getByName(ip)), ip + " 应被判为不可出站");
        }
        for (String ip : new String[]{"1.1.1.1", "8.8.8.8", "93.184.216.34"}) {
            assertFalse(IpEgressGuard.isBlocked(InetAddress.getByName(ip)), ip + " 公网应放行");
        }
    }

    @Test
    void kpi求值器_超深嵌套_拒绝而非栈溢出() {
        // L-12：深层嵌套括号应被深度上限拦下抛 IllegalArgumentException，而非 StackOverflowError
        String deep = "(".repeat(500) + "1" + ")".repeat(500);
        assertThrows(IllegalArgumentException.class,
                () -> ArithmeticEvaluator.eval(deep, java.util.Map.of()), "超深嵌套应被拒");
        // 正常浅表达式仍可求值
        assertDoesNotThrow(() -> ArithmeticEvaluator.eval("(1 + 2) * 3", java.util.Map.of()));
    }

    @Test
    void 登录限速_超窗口上限即拒_成功清零() {
        LoginRateLimiter rl = new LoginRateLimiter(3, 300);
        assertTrue(rl.allow("1.2.3.4"));
        assertTrue(rl.allow("1.2.3.4"));
        assertTrue(rl.allow("1.2.3.4"));
        assertFalse(rl.allow("1.2.3.4"), "第 4 次应被限速拒绝");
        assertTrue(rl.allow("5.6.7.8"), "另一 IP 不受影响");
        rl.reset("1.2.3.4");
        assertTrue(rl.allow("1.2.3.4"), "成功清零后可再尝试");
    }
}
