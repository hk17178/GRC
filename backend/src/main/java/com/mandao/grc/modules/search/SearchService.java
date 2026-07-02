package com.mandao.grc.modules.search;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 全局全文检索（需求 V1.9 §13.3：跨法规/制度/评估/审计/义务/供应商/知识库检索）。
 *
 * 隔离：方法 @Transactional 且位于 modules 包，OrgScopeAspect 注入 visible_orgs，
 * 各业务表的 RLS 自动裁剪——用户只能搜到自己可见组织的数据，无须在 SQL 里写 org 过滤。
 *
 * 实现：对 8 类对象做 ILIKE 模糊匹配（标题/编码/名称），UNION ALL 汇总、限量返回。
 * P1 先做词面匹配；语义检索（pgvector）已在 AI 问答模块，后续可并入。
 */
@Service
public class SearchService {

    @PersistenceContext
    private EntityManager em;

    /**
     * 全局搜索。
     *
     * @param q 关键词（词面 ILIKE 匹配）
     * @return 命中列表（module/id/title/sub），按来源分组排序，总量截断 40 条
     */
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<SearchHit> search(String q) {
        if (q == null || q.isBlank()) {
            return List.of();
        }
        String like = "%" + q.trim() + "%";
        // UNION ALL 各来源（列结构统一：src/id/title/sub），单来源限 8 条防某类刷屏
        String sql =
            "SELECT * FROM ("
            + "  (SELECT 'regulation' AS src, id, CAST(title AS TEXT) AS title, CAST(code AS TEXT) AS sub FROM regulation WHERE title ILIKE :q OR code ILIKE :q LIMIT 8)"
            + "  UNION ALL (SELECT 'crawled', id, CAST(title AS TEXT), CAST(COALESCE(issuer,'') AS TEXT) FROM regulation_crawled WHERE title ILIKE :q LIMIT 8)"
            + "  UNION ALL (SELECT 'policy', id, CAST(title AS TEXT), CAST(code AS TEXT) FROM policy WHERE title ILIKE :q OR code ILIKE :q LIMIT 8)"
            + "  UNION ALL (SELECT 'assessment', id, CAST(title AS TEXT), CAST(COALESCE(period,'') AS TEXT) FROM assessment WHERE title ILIKE :q LIMIT 8)"
            + "  UNION ALL (SELECT 'obligation', id, CAST(title AS TEXT), CAST(code AS TEXT) FROM obligation WHERE title ILIKE :q OR code ILIKE :q LIMIT 8)"
            + "  UNION ALL (SELECT 'vendor', id, CAST(name AS TEXT), CAST(code AS TEXT) FROM vendor WHERE name ILIKE :q OR code ILIKE :q LIMIT 8)"
            + "  UNION ALL (SELECT CASE WHEN audit_type='EXTERNAL' THEN 'extaudit' ELSE 'audit' END, id, CAST(title AS TEXT), CAST(audit_type AS TEXT) FROM audit_plan WHERE title ILIKE :q LIMIT 8)"
            + "  UNION ALL (SELECT 'finding', id, CAST(title AS TEXT), CAST(severity AS TEXT) FROM audit_finding WHERE title ILIKE :q LIMIT 8)"
            + "  UNION ALL (SELECT 'kb', id, CAST(title AS TEXT), CAST(source_type AS TEXT) FROM kb_document WHERE title ILIKE :q LIMIT 8)"
            + ") s LIMIT 40";
        List<Object[]> rows = em.createNativeQuery(sql).setParameter("q", like).getResultList();
        return rows.stream()
                .map(r -> new SearchHit((String) r[0], ((Number) r[1]).longValue(), (String) r[2], (String) r[3]))
                .toList();
    }

    /** 一条搜索命中：来源模块 / 对象 id / 标题 / 附注（编码/机构/等级等）。 */
    public record SearchHit(String module, Long id, String title, String sub) {
    }
}
