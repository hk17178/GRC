package com.mandao.grc.modules.regulation.crawler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 通用 HTTP 源爬虫（HTTP）：抓取配置的列表页，按 CSS 选择器解析为法规列表（jsoup）。
 *
 * 不硬编码任何具体政府站点——目标站与选择器由运营在"追踪源"里配置（合规与 robots 由部署方把关）。
 * config（JSON）字段：
 *  - listSelector：列表项选择器（每条法规一个元素）
 *  - titleSelector：标题选择器（相对列表项，缺省取列表项自身文本）
 *  - linkSelector：链接选择器（相对列表项，取 href；缺省取列表项内首个 a）
 *  - dateSelector：日期选择器（可选）
 *  - issuer / category：固定值（可选，整源统一）
 */
@Component
public class HttpLawCrawler implements LawCrawler {

    private final ObjectMapper objectMapper;

    public HttpLawCrawler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public SourceType type() {
        return SourceType.HTTP;
    }

    @Override
    public List<CrawledLaw> fetch(RegulationSource source) {
        if (source.getUrl() == null || source.getUrl().isBlank()) {
            throw new IllegalArgumentException("HTTP 源未配置 url");
        }
        assertPublicHttpUrl(source.getUrl());   // 安全评审 H-2：抓取前挡内网/元数据 SSRF
        JsonNode cfg = parseConfig(source.getConfig());
        String listSel = text(cfg, "listSelector");
        String titleSel = text(cfg, "titleSelector");
        String linkSel = text(cfg, "linkSelector");
        String dateSel = text(cfg, "dateSelector");
        String issuer = text(cfg, "issuer");
        String category = text(cfg, "category");

        try {
            Document doc = Jsoup.connect(source.getUrl())
                    .userAgent("Mozilla/5.0 (compatible; GRC-LawTracker/1.0)")
                    .timeout(15000)
                    .get();
            // 未配置 listSelector（如常见源模板）→ 通用兜底：扫全页 a[href]，按标题特征启发式提取，
            // 配合源级关键字过滤收窄；用户可随后填精确选择器提升准确度。
            if (listSel == null) {
                return genericExtract(doc, issuer, category);
            }
            Elements items = doc.select(listSel);
            List<CrawledLaw> out = new ArrayList<>();
            for (Element item : items) {
                String title = titleSel != null ? textOf(item.selectFirst(titleSel)) : item.text();
                if (title == null || title.isBlank()) {
                    continue;
                }
                Element linkEl = linkSel != null ? item.selectFirst(linkSel) : item.selectFirst("a");
                String url = linkEl != null ? linkEl.absUrl("href") : null;
                String dedup = (url != null && !url.isBlank()) ? url : title;
                LocalDate date = null; // 日期格式各站不同，P1 仅取文本不强解析，避免误判
                String dateText = dateSel != null ? textOf(item.selectFirst(dateSel)) : null;
                String summary = dateText != null ? ("发布信息：" + dateText) : null;
                out.add(new CrawledLaw(dedup, title.trim(), null, issuer, category, date, url, summary));
            }
            return out;
        } catch (Exception e) {
            throw new RuntimeException("抓取失败：" + e.getMessage(), e);
        }
    }

    /** 法规标题特征词（含其一 + 长度合适即视为疑似法规标题，滤掉导航/页脚噪声）。 */
    private static final String[] TITLE_HINTS = {
            "法", "条例", "办法", "规定", "规则", "通知", "公告", "指南", "指引", "管理",
            "规范", "标准", "意见", "决定", "批复", "细则", "方案", "制度", "通则", "准则", "令" };

    /**
     * 通用兜底提取：无 listSelector 时扫全页 a[href]，取"疑似法规标题"的链接（去重）。
     * 尽力而为——目标是模板源开箱即用产出相关条目，用户可再配精确选择器精修。
     */
    public List<CrawledLaw> genericExtract(Document doc, String issuer, String category) {
        List<CrawledLaw> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Element a : doc.select("a[href]")) {
            String title = a.text() == null ? "" : a.text().trim();
            String url = a.absUrl("href");
            if (!looksLikeLawTitle(title) || url == null || url.isBlank() || !url.startsWith("http")) {
                continue;
            }
            if (!seen.add(url)) {
                continue;
            }
            out.add(new CrawledLaw(url, title, null, issuer, category, null, url, null));
            if (out.size() >= 100) {
                break;   // 单次上限，防超大页面
            }
        }
        return out;
    }

    private boolean looksLikeLawTitle(String title) {
        if (title == null) {
            return false;
        }
        int len = title.length();
        if (len < 6 || len > 80) {
            return false;   // 太短多为导航/按钮，太长多为整段
        }
        for (String h : TITLE_HINTS) {
            if (title.contains(h)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 安全评审 H-2：抓取任意配置 URL 前做 SSRF 校验——仅允许 http/https，且主机解析出的每个地址
     * 都不得为回环/内网/链路本地(含云元数据 169.254.169.254)/通配/组播地址，防运营或攻击者把追踪源
     * 指向内网服务或元数据端点探测、外泄内网响应。（DNS 重绑定 TOCTOU 与 IPv6 ULA 覆盖为后续加固项。）
     */
    public void assertPublicHttpUrl(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (Exception e) {
            throw new IllegalArgumentException("追踪源 URL 非法：" + url);
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException("追踪源仅允许 http/https：" + url);
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("追踪源 URL 缺少主机名：" + url);
        }
        try {
            for (InetAddress addr : InetAddress.getAllByName(host)) {
                if (addr.isLoopbackAddress() || addr.isSiteLocalAddress() || addr.isLinkLocalAddress()
                        || addr.isAnyLocalAddress() || addr.isMulticastAddress()) {
                    throw new IllegalArgumentException("SSRF 防护：追踪源 " + host + " 解析到内网/保留地址 "
                            + addr.getHostAddress() + "，已拒绝抓取");
                }
            }
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("追踪源主机无法解析：" + host);
        }
    }

    private JsonNode parseConfig(String config) {
        try {
            return config == null || config.isBlank()
                    ? objectMapper.createObjectNode()
                    : objectMapper.readTree(config);
        } catch (Exception e) {
            throw new IllegalArgumentException("源配置 JSON 非法：" + e.getMessage(), e);
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() || v.asText().isBlank() ? null : v.asText();
    }

    private String textOf(Element el) {
        return el == null ? null : el.text();
    }
}
