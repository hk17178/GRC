package com.mandao.grc.modules.search;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 全局搜索端点：GET /api/search?q=关键词。
 *
 * 只读、可见域由 RLS 裁剪；不设写权限门控（登录即用，各模块数据本就按组织隔离）。
 */
@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SearchService service;

    public SearchController(SearchService service) {
        this.service = service;
    }

    @GetMapping
    public List<SearchService.SearchHit> search(@RequestParam String q) {
        return service.search(q);
    }
}
