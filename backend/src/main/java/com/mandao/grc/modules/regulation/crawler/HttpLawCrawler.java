package com.mandao.grc.modules.regulation.crawler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

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
        JsonNode cfg = parseConfig(source.getConfig());
        String listSel = text(cfg, "listSelector");
        if (listSel == null) {
            throw new IllegalArgumentException("HTTP 源未配置 listSelector");
        }
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
