package com.mandao.grc.kernel;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.net.InetAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 告警通道外推（八轮 8-1 / 评估报告 B5）：把规则引擎/到期扫描产出的告警推送到企微群机器人。
 *
 * 边界声明：第一批只落地 企微 Webhook 单通道 + 发送成功/失败留痕（notify_send_log）；
 * 邮件/短信通道后置（通道管理里可登记，引擎暂不外推）。失败不重试——留痕即达标，
 * 站内提醒（reminder_dispatch_log）始终是权威记录，外推只是"多喊一嗓子"。
 *
 * 结构上遵守 7-7 教训：HTTP 外呼绝不夹在数据库事务里——
 *   loadWecomChannels()（短事务读通道）→ HTTP 裸跑 → logResults()（短事务写留痕）。
 * 与内核其它服务同范式：native SQL + SET LOCAL 系统全域可见（notify_config 有 RLS）。
 *
 * 安全：webhook 主机白名单（grc.notify.webhook-allowlist）+ DNS 私网复核（防 SSRF），
 * 5s 连接 / 10s 读超时。
 */
@Service
public class AlertPushService {

    private static final Logger log = LoggerFactory.getLogger(AlertPushService.class);

    @PersistenceContext
    private EntityManager em;

    private final List<String> allowlist;

    /** 自注入代理（事务方法自调用不走代理会丢事务——经代理调用 load/log 两个短事务）。 */
    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.context.annotation.Lazy
    private AlertPushService self;

    public AlertPushService(
            @Value("${grc.notify.webhook-allowlist:qyapi.weixin.qq.com}") String allowlist) {
        this.allowlist = List.of(allowlist.split(","));
    }

    /** 新产告警（引擎/扫描的产物，推送与留痕的最小载荷）。 */
    public record Alert(long orgId, String message) {
    }

    /** 已配置的企微通道。 */
    public record Channel(long orgId, String name, String target) {
    }

    /** 推送结果（写留痕用）。 */
    public record SendResult(long orgId, String channelType, String target, String message,
                             boolean success, String error) {
    }

    /** 外推入口：无新告警或无通道则静默返回；HTTP 在事务外执行。 */
    public int pushAll(List<Alert> alerts) {
        if (alerts == null || alerts.isEmpty()) {
            return 0;
        }
        List<Channel> channels = self.loadWecomChannels();
        if (channels.isEmpty()) {
            return 0;
        }
        List<SendResult> results = new ArrayList<>();
        for (Alert a : alerts) {
            for (Channel c : channels) {
                // 通道属集团（org1）推所有告警；属子公司只推本组织告警
                if (c.orgId() != 1L && c.orgId() != a.orgId()) {
                    continue;
                }
                results.add(sendWecom(c, a));
            }
        }
        self.logResults(results);
        long ok = results.stream().filter(SendResult::success).count();
        if (!results.isEmpty()) {
            log.info("告警外推：企微 {} 条（成功 {}，失败 {}）", results.size(), ok, results.size() - ok);
        }
        return (int) ok;
    }

    /** 读启用的企微通道（notify_config kind=CHANNEL，detail.type=WECOM 且有 target）。 */
    @Transactional(readOnly = true)
    public List<Channel> loadWecomChannels() {
        // 架构治理包 A26：会话可见域走 set_config 参数化（防注入样板）
        VisibleOrgsSql.setAllOrgs(em);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                        "SELECT org_id, name, detail FROM notify_config WHERE kind = 'CHANNEL' AND enabled = TRUE")
                .getResultList();
        List<Channel> out = new ArrayList<>();
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        for (Object[] r : rows) {
            try {
                var d = mapper.readTree((String) r[2] == null ? "{}" : (String) r[2]);
                if ("WECOM".equals(d.path("type").asText()) && !d.path("target").asText().isBlank()) {
                    out.add(new Channel(((Number) r[0]).longValue(), (String) r[1], d.path("target").asText()));
                }
            } catch (Exception ignore) {
                // 坏 JSON 的通道跳过（配置校验属通知体验包）
            }
        }
        return out;
    }

    /** 单条企微推送（事务外；白名单+私网复核+超时）。 */
    private SendResult sendWecom(Channel c, Alert a) {
        try {
            validateWebhook(c.target());
            SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
            f.setConnectTimeout(5000);
            f.setReadTimeout(10000);
            RestClient http = RestClient.builder().requestFactory(f).build();
            http.post().uri(c.target())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("msgtype", "text", "text", Map.of("content", "【GRC 告警】" + a.message())))
                    .retrieve()
                    .toBodilessEntity();
            return new SendResult(a.orgId(), "WECOM", c.target(), a.message(), true, null);
        } catch (Exception e) {
            String err = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            return new SendResult(a.orgId(), "WECOM", c.target(), a.message(), false,
                    err.length() > 480 ? err.substring(0, 480) : err);
        }
    }

    /** webhook 目的地校验：主机白名单 + DNS 私网复核（与 AI 出站守卫同口径）。 */
    private void validateWebhook(String url) throws Exception {
        String host = URI.create(url).getHost();
        if (host == null || allowlist.stream().map(String::trim).noneMatch(host::equalsIgnoreCase)) {
            throw new IllegalStateException("webhook 主机不在白名单：" + host
                    + "（grc.notify.webhook-allowlist 可配置追加）");
        }
        for (InetAddress addr : InetAddress.getAllByName(host)) {
            if (addr.isLoopbackAddress() || addr.isSiteLocalAddress() || addr.isLinkLocalAddress()) {
                throw new IllegalStateException("SSRF 防护：webhook 主机解析到内网地址，已拒绝");
            }
        }
    }

    /** 发送留痕落库（短事务）。 */
    @Transactional
    public void logResults(List<SendResult> results) {
        for (SendResult r : results) {
            em.createNativeQuery("INSERT INTO notify_send_log(org_id, channel_type, target, message, success, error) "
                            + "VALUES (:org, :ct, :tg, :msg, :ok, :err)")
                    .setParameter("org", r.orgId())
                    .setParameter("ct", r.channelType())
                    .setParameter("tg", r.target())
                    .setParameter("msg", r.message())
                    .setParameter("ok", r.success())
                    .setParameter("err", r.error())
                    .executeUpdate();
        }
    }

    /** 发送留痕查询（通知中心「发送留痕」区，最近 limit 条）。 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> recentLogs(int limit) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                        "SELECT id, org_id, channel_type, target, message, success, error, "
                                + "CAST(EXTRACT(EPOCH FROM created_at) * 1000 AS bigint) "
                                + "FROM notify_send_log ORDER BY id DESC LIMIT " + Math.min(Math.max(limit, 1), 200))
                .getResultList();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object[] r : rows) {
            Map<String, Object> m = new java.util.HashMap<>();
            m.put("id", ((Number) r[0]).longValue());
            m.put("orgId", ((Number) r[1]).longValue());
            m.put("channelType", r[2]);
            // 脱敏：webhook key 不外显，只留主机与尾巴
            String tg = (String) r[3];
            m.put("target", tg != null && tg.length() > 46 ? tg.substring(0, 40) + "…" + tg.substring(tg.length() - 4) : tg);
            m.put("message", r[4]);
            m.put("success", r[5]);
            m.put("error", r[6]);
            m.put("createdAtMs", ((Number) r[7]).longValue());
            out.add(m);
        }
        return out;
    }
}
