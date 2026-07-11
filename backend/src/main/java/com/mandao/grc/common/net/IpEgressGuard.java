package com.mandao.grc.common.net;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * 统一出站 SSRF 守卫（安全评审 L-2/L-6）：把爬虫 / AI 出站 / webhook 三处各自实现、覆盖不一的
 * 私网判定统一到一处，并补齐此前遗漏的保留段——IPv6 ULA(fc00::/7)、CGNAT(100.64.0.0/10)、
 * benchmarking(198.18.0.0/15)，杜绝解析到这些地址仍被放行。
 *
 * 说明：本守卫在"解析后、连接前"校验；对 DNS 重绑定 TOCTOU（校验与连接两次解析间被改指内网）
 * 的彻底防护需按解析出的 IP 定连（pin IP + 带 Host 头），属更深加固，此处先统一并收严地址判定。
 */
public final class IpEgressGuard {

    private IpEgressGuard() {
    }

    /** 校验主机名解析出的每个地址均为公网可路由；命中内网/保留段抛 IllegalStateException。 */
    public static void assertPublicHost(String host) {
        if (host == null || host.isBlank()) {
            throw new IllegalStateException("出站目的地缺少主机名");
        }
        try {
            InetAddress[] addrs = InetAddress.getAllByName(host);
            for (InetAddress addr : addrs) {
                if (isBlocked(addr)) {
                    throw new IllegalStateException("SSRF 防护：主机 " + host + " 解析到内网/保留地址 "
                            + addr.getHostAddress() + "，已拒绝出站");
                }
            }
        } catch (UnknownHostException e) {
            throw new IllegalStateException("出站主机无法解析：" + host);
        }
    }

    /** 是否为不可出站地址（内网/回环/链路本地/组播/通配 + IPv6 ULA / CGNAT / benchmarking 保留段）。 */
    public static boolean isBlocked(InetAddress a) {
        if (a.isLoopbackAddress() || a.isAnyLocalAddress() || a.isLinkLocalAddress()
                || a.isSiteLocalAddress() || a.isMulticastAddress()) {
            return true;
        }
        byte[] b = a.getAddress();
        if (b.length == 4) {
            int b0 = b[0] & 0xFF;
            int b1 = b[1] & 0xFF;
            if (b0 == 100 && b1 >= 64 && b1 <= 127) {
                return true;   // CGNAT 100.64.0.0/10
            }
            if (b0 == 198 && (b1 == 18 || b1 == 19)) {
                return true;   // benchmarking 198.18.0.0/15
            }
            if (b0 == 192 && b1 == 0 && (b[2] & 0xFF) == 0) {
                return true;   // IETF protocol assignments 192.0.0.0/24
            }
        } else if (b.length == 16) {
            if ((b[0] & 0xFE) == 0xFC) {
                return true;   // IPv6 ULA fc00::/7（isSiteLocalAddress 只覆盖已废弃的 fec0::/10）
            }
        }
        return false;
    }
}
