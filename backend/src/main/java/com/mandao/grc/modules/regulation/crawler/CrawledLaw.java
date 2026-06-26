package com.mandao.grc.modules.regulation.crawler;

import java.time.LocalDate;

/**
 * 爬虫产出的一条法规（未落库的传输对象）。
 *
 * @param dedupKey    去重键（url 或文号；同组织内唯一识别一条法规）
 * @param title       标题
 * @param docNo       发文字号
 * @param issuer      发布机关
 * @param category    分类（体系/主题）
 * @param publishDate 发布日期
 * @param url         原文链接
 * @param summary     摘要
 */
public record CrawledLaw(String dedupKey, String title, String docNo, String issuer,
                         String category, LocalDate publishDate, String url, String summary) {
}
