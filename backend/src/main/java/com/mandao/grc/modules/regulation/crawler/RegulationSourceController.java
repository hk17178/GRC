package com.mandao.grc.modules.regulation.crawler;

import com.mandao.grc.common.auth.CurrentUserContext;
import com.mandao.grc.modules.rbac.RequiresPermission;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 法规追踪源 + 采集 REST 端点：/api/regulation-sources、/api/crawled-regulations。
 *
 * 写操作（新增源/抓取/启停/删除）门控 "law"（法规跟踪菜单写权限）。actor 取登录态。
 */
@RestController
@RequestMapping("/api")
public class RegulationSourceController {

    private final RegulationCrawlService service;

    public RegulationSourceController(RegulationCrawlService service) {
        this.service = service;
    }

    /** 追踪源列表。 */
    @GetMapping("/regulation-sources")
    public List<RegulationSource> listSources() {
        return service.listSources();
    }

    /** 新增追踪源。 */
    @PostMapping("/regulation-sources")
    @RequiresPermission("law")
    public RegulationSource addSource(@RequestBody AddSourceRequest req) {
        return service.addSource(req.orgId(), req.name(),
                req.sourceType() == null ? SourceType.SAMPLE : req.sourceType(),
                req.url(), req.config(), req.frequency(), actor());
    }

    /** 立即抓取某源。 */
    @PostMapping("/regulation-sources/{id}/crawl")
    @RequiresPermission("law")
    public RegulationCrawlService.CrawlResult crawl(@PathVariable Long id) {
        return service.crawl(id, actor());
    }

    /** 启用/停用源。 */
    @PutMapping("/regulation-sources/{id}/enabled")
    @RequiresPermission("law")
    public RegulationSource setEnabled(@PathVariable Long id, @RequestParam boolean enabled) {
        return service.setEnabled(id, enabled);
    }

    /** 删除源。 */
    @DeleteMapping("/regulation-sources/{id}")
    @RequiresPermission("law")
    public void delete(@PathVariable Long id) {
        service.deleteSource(id);
    }

    /** 某源的采集法规。 */
    @GetMapping("/regulation-sources/{id}/items")
    public List<RegulationCrawled> itemsOf(@PathVariable Long id) {
        return service.listCrawledBySource(id);
    }

    /** 全部采集法规（采集流）。 */
    @GetMapping("/crawled-regulations")
    public List<RegulationCrawled> allCrawled() {
        return service.listCrawled();
    }

    private String actor() {
        String u = CurrentUserContext.get();
        return u == null || u.isBlank() ? "anonymous" : u;
    }

    /** 新增源请求体。 */
    public record AddSourceRequest(Long orgId, String name, SourceType sourceType, String url,
                                   String config, String frequency) {
    }
}
