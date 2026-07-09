package com.mandao.grc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mandao.grc.modules.regulation.crawler.CrawledLaw;
import com.mandao.grc.modules.regulation.crawler.HttpLawCrawler;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HTTP 爬虫「通用兜底提取」单元测试（无网络，纯 jsoup 解析静态 HTML）。
 * 验证：未配 listSelector 时，扫全页 a[href] 按标题特征启发式提取疑似法规标题，滤掉导航噪声、去重。
 * 这是常见源模板「开箱即用」的关键——不再因缺 listSelector 直接报错。
 */
class HttpLawCrawlerTest {

    private final HttpLawCrawler crawler = new HttpLawCrawler(new ObjectMapper());

    @Test
    void 通用兜底_提取疑似法规标题_滤噪声_去重() {
        String html = "<html><body>"
                + "<a href='http://x.gov.cn/1.html'>网络数据安全管理条例</a>"          // 命中（条例）
                + "<a href='http://x.gov.cn/2.html'>支付机构反洗钱管理办法</a>"        // 命中（办法）
                + "<a href='http://x.gov.cn/3.html'>金融数据安全分级指南</a>"          // 命中（指南）
                + "<a href='http://x.gov.cn/1.html'>网络数据安全管理条例</a>"          // 重复 URL → 去重
                + "<a href='http://x.gov.cn/home'>首页</a>"                            // 噪声（太短/非法规词）
                + "<a href='http://x.gov.cn/more'>更多>></a>"                          // 噪声
                + "<a href='javascript:void(0)'>登录</a>"                              // 非 http
                + "</body></html>";
        Document doc = Jsoup.parse(html, "http://x.gov.cn/");

        List<CrawledLaw> out = crawler.genericExtract(doc, "某部委", "数据安全");
        assertEquals(3, out.size(), "应提取 3 条疑似法规标题（去重 + 滤噪声）");
        assertTrue(out.stream().anyMatch(c -> c.title().contains("网络数据安全管理条例")));
        assertTrue(out.stream().anyMatch(c -> c.title().contains("反洗钱管理办法")));
        assertTrue(out.stream().allMatch(c -> "某部委".equals(c.issuer()) && "数据安全".equals(c.category())),
                "应带上源的机构/分类");
        assertTrue(out.stream().noneMatch(c -> c.title().equals("首页") || c.title().contains("更多")),
                "导航噪声不应入选");
    }
}
