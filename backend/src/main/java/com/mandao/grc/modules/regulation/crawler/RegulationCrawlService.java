package com.mandao.grc.modules.regulation.crawler;

import com.mandao.grc.modules.audit.HashChainService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 法规采集服务：管理追踪源 + 触发抓取（采集→去重→落库→留痕）。
 *
 * 隔离：方法带 @Transactional 且位于 modules 包，{@link com.mandao.grc.common.isolation.OrgScopeAspect}
 * 注入 app.visible_orgs，RLS 自动裁剪/校验；源与采集条目均携 org_id。
 *
 * 爬虫按 {@link SourceType} 可插拔分发（SAMPLE 内置示例、HTTP 通用抓取）。
 */
@Service
public class RegulationCrawlService {

    private final RegulationSourceRepository sourceRepo;
    private final RegulationCrawledRepository crawledRepo;
    private final HashChainService hashChainService;
    private final Map<SourceType, LawCrawler> crawlers = new EnumMap<>(SourceType.class);

    public RegulationCrawlService(RegulationSourceRepository sourceRepo,
                                  RegulationCrawledRepository crawledRepo,
                                  HashChainService hashChainService,
                                  List<LawCrawler> crawlerList) {
        this.sourceRepo = sourceRepo;
        this.crawledRepo = crawledRepo;
        this.hashChainService = hashChainService;
        for (LawCrawler c : crawlerList) {
            crawlers.put(c.type(), c);
        }
    }

    // ---------- 源管理 ----------

    @Transactional(readOnly = true)
    public List<RegulationSource> listSources() {
        return sourceRepo.findAllByOrderByIdDesc();
    }

    @Transactional
    public RegulationSource addSource(Long orgId, String name, SourceType type, String url, String config,
                                      String frequency, String actor) {
        RegulationSource s = new RegulationSource(orgId, name, type, url, config, frequency);
        RegulationSource saved = sourceRepo.save(s);
        hashChainService.append(orgId, "REG_SOURCE_ADD", actor, "REG_SOURCE:" + saved.getId(),
                "新增法规追踪源：" + name + "（" + saved.getSourceType() + "）");
        return saved;
    }

    @Transactional
    public RegulationSource setEnabled(Long id, boolean enabled) {
        RegulationSource s = getSource(id);
        s.setEnabled(enabled);
        return sourceRepo.save(s);
    }

    @Transactional
    public void deleteSource(Long id) {
        RegulationSource s = getSource(id);
        sourceRepo.delete(s);
    }

    // ---------- 抓取 ----------

    /**
     * 触发某源抓取：采集 → 按 (org,dedup_key) 去重 → 落库新条目 → 更新源状态 + 留痕。
     *
     * @return 抓取结果（命中总数 / 新增数）
     */
    @Transactional
    public CrawlResult crawl(Long sourceId, String actor) {
        RegulationSource s = getSource(sourceId);
        LawCrawler crawler = crawlers.get(s.getSourceType());
        if (crawler == null) {
            throw new IllegalStateException("无可用爬虫，源类型：" + s.getSourceType());
        }
        int newCount = 0;
        int hit = 0;
        try {
            List<CrawledLaw> laws = crawler.fetch(s);
            hit = laws.size();
            for (CrawledLaw law : laws) {
                if (law.dedupKey() == null || law.dedupKey().isBlank()) {
                    continue;
                }
                if (!crawledRepo.existsByDedupKey(law.dedupKey())) {
                    crawledRepo.save(new RegulationCrawled(s.getOrgId(), s.getId(), law));
                    newCount++;
                }
            }
            s.markFetched(hit, null);
            sourceRepo.save(s);
            hashChainService.append(s.getOrgId(), "REG_SOURCE_CRAWL", actor, "REG_SOURCE:" + s.getId(),
                    "采集源「" + s.getName() + "」命中 " + hit + " 条，新增 " + newCount + " 条");
            return new CrawlResult(hit, newCount, null, s.getConsecutiveMiss(), s.getOrgId(), s.getName());
        } catch (RuntimeException e) {
            s.markFetched(hit, e.getMessage());
            sourceRepo.save(s);
            // 抓取失败不抛断（记录到源状态供前端展示"采集健康"）
            return new CrawlResult(hit, newCount, e.getMessage(), s.getConsecutiveMiss(), s.getOrgId(), s.getName());
        }
    }

    // ---------- 采集结果查询 ----------

    @Transactional(readOnly = true)
    public List<RegulationCrawled> listCrawled() {
        return crawledRepo.findAllByOrderByFetchedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<RegulationCrawled> listCrawledBySource(Long sourceId) {
        return crawledRepo.findBySourceIdOrderByFetchedAtDesc(sourceId);
    }

    private RegulationSource getSource(Long id) {
        return sourceRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("追踪源不存在或不可见：id=" + id));
    }

    /** 抓取结果（B25：附源当前连续未抓到次数与源标识，供调度器判定失效告警）。 */
    public record CrawlResult(int hit, int added, String error, int consecutiveMiss, Long orgId, String sourceName) {
    }
}
