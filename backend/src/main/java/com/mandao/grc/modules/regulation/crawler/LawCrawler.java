package com.mandao.grc.modules.regulation.crawler;

import java.util.List;

/**
 * 法规爬虫抽象（可插拔，类比 AI 的 LlmProvider）。
 *
 * 每种 {@link SourceType} 对应一个实现；{@link com.mandao.grc.modules.regulation.crawler.RegulationCrawlService}
 * 按源类型分发。新增源类型 = 加一个实现 Bean，引擎与服务不变。
 */
public interface LawCrawler {

    /** 本爬虫处理的源类型。 */
    SourceType type();

    /**
     * 抓取并解析为法规列表。
     *
     * @param source 追踪源配置
     * @return 解析出的法规（未去重、未落库）
     * @throws RuntimeException 抓取/解析失败（由服务捕获并记到源的 last_error）
     */
    List<CrawledLaw> fetch(RegulationSource source);
}
