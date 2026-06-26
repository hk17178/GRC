package com.mandao.grc.modules.regulation.crawler;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * 内置示例源爬虫（SAMPLE）：返回一批贴近非银行支付机构监管的演示法规，不外联。
 *
 * 默认即可用，便于演示"采集→去重→排版展示"的完整链路，而无需配置真实站点与外网抓取
 * （真实抓取由 {@link HttpLawCrawler} 按运营配置进行，涉及外网与合规由部署方把关）。
 * dedup_key 用稳定的文号，保证重复"抓取"不产生重复条目（演示增量去重）。
 */
@Component
public class SampleLawCrawler implements LawCrawler {

    @Override
    public SourceType type() {
        return SourceType.SAMPLE;
    }

    @Override
    public List<CrawledLaw> fetch(RegulationSource source) {
        return List.of(
                new CrawledLaw("PBOC-2024-1", "非银行支付机构监督管理条例实施细则",
                        "中国人民银行令〔2024〕第1号", "中国人民银行", "支付清算",
                        LocalDate.of(2024, 5, 1), "https://www.pbc.gov.cn/sample/2024-1",
                        "明确支付机构分类监管、备付金集中存管与交易日志留存要求。"),
                new CrawledLaw("PBOC-2023-7", "非银行支付机构客户备付金存管办法",
                        "中国人民银行令〔2023〕第7号", "中国人民银行", "备付金",
                        LocalDate.of(2023, 11, 10), "https://www.pbc.gov.cn/sample/2023-7",
                        "规范客户备付金的集中存管、计息与使用监督。"),
                new CrawledLaw("CAC-2024-3", "个人信息保护合规审计管理办法",
                        "国家网信办公告〔2024〕第3号", "国家互联网信息办公室", "数据合规",
                        LocalDate.of(2024, 3, 22), "https://www.cac.gov.cn/sample/2024-3",
                        "明确处理个人信息达一定规模的处理者应定期开展合规审计。"),
                new CrawledLaw("PBOC-AML-2024", "支付机构反洗钱和反恐怖融资管理办法",
                        "中国人民银行令〔2024〕第2号", "中国人民银行", "反洗钱",
                        LocalDate.of(2024, 7, 1), "https://www.pbc.gov.cn/sample/aml-2024",
                        "强化支付机构客户尽职调查、可疑交易报告与名单监控义务。"),
                new CrawledLaw("JRT-DSEC-2023", "金融数据安全 数据安全分级指南",
                        "JR/T 0197—2020", "中国人民银行", "数据安全",
                        LocalDate.of(2023, 9, 15), "https://www.pbc.gov.cn/sample/jrt-0197",
                        "给出金融数据安全分级方法与定级要素，指导分级保护。"));
    }
}
