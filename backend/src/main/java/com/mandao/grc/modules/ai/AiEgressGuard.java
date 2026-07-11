package com.mandao.grc.modules.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

/**
 * AI 出站守卫（七轮 7-10 / 评估报告 B6 红线 + 7-7 超时）。
 *
 * 三层防护，全部在真正发起外呼前校验（LOCAL 离线模式不经过本类）：
 *  1) 数据不出域总开关 grc.ai.outbound.enabled——支付机构隔离网环境置 false，
 *     任何大模型外呼直接拒绝（AI-5 红线）；
 *  2) 出站白名单 grc.ai.outbound.allowlist——目的主机不在名单即拒绝，
 *     防止 baseUrl 被配置成任意外网地址把语料送去不明服务；
 *  3) SSRF 防护——DNS 解析后逐地址复核，命中回环/内网/链路本地地址一律拒绝
 *     （防白名单域名被解析劫持指向内网元数据服务等）。
 *
 * 同时统一供给带超时的 RestClient（7-7）：LLM 外呼曾无任何超时且在事务内，
 * 端点卡死会拖住数据库连接直至耗尽连接池——连接/读超时是稳定性底线。
 */
@Component
public class AiEgressGuard {

    private static final Logger log = LoggerFactory.getLogger(AiEgressGuard.class);

    private final boolean outboundEnabled;
    private final List<String> allowlist;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;

    public AiEgressGuard(
            @Value("${grc.ai.outbound.enabled:true}") boolean outboundEnabled,
            @Value("${grc.ai.outbound.allowlist:api.anthropic.com,api.openai.com,dashscope.aliyuncs.com,open.bigmodel.cn,api.deepseek.com,api.moonshot.cn}") String allowlist,
            @Value("${grc.ai.http.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${grc.ai.http.read-timeout-ms:60000}") int readTimeoutMs) {
        this.outboundEnabled = outboundEnabled;
        this.allowlist = Arrays.stream(allowlist.split(","))
                .map(String::trim).filter(s -> !s.isBlank()).toList();
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
        if (!outboundEnabled) {
            log.info("[安全自检] AI 数据不出域开关已关闭（grc.ai.outbound.enabled=false）——所有大模型外呼将被拒绝");
        }
    }

    /**
     * 校验出站目的地并返回带超时的 RestClient。校验失败抛 IllegalStateException（用户可见的明确原因）。
     */
    public RestClient clientFor(String baseUrl) {
        validate(baseUrl);
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(connectTimeoutMs);
        f.setReadTimeout(readTimeoutMs);
        return RestClient.builder().baseUrl(baseUrl).requestFactory(f).build();
    }

    /** 出站三层校验：开关 → 白名单 → DNS 私网复核。 */
    void validate(String baseUrl) {
        if (!outboundEnabled) {
            throw new IllegalStateException("AI 数据不出域开关已关闭（grc.ai.outbound.enabled=false），禁止外呼大模型；如需启用请联系平台管理员");
        }
        String host;
        try {
            host = URI.create(baseUrl).getHost();
        } catch (Exception e) {
            throw new IllegalStateException("AI 接入地址非法：" + baseUrl);
        }
        if (host == null || host.isBlank()) {
            throw new IllegalStateException("AI 接入地址缺少主机名：" + baseUrl);
        }
        String h = host.toLowerCase();
        if (allowlist.stream().noneMatch(a -> h.equals(a.toLowerCase()))) {
            throw new IllegalStateException("AI 出站主机不在白名单：" + host
                    + "（可经 grc.ai.outbound.allowlist 配置追加，需安全评审）");
        }
        // 统一出站守卫（L-2/L-6）：解析后逐地址判内网/保留段（含 IPv6 ULA/CGNAT/benchmarking）
        com.mandao.grc.common.net.IpEgressGuard.assertPublicHost(host);
    }
}
