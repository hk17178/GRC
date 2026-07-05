package com.mandao.grc.kernel;

import com.mandao.grc.common.isolation.IsolationContext;
import com.mandao.grc.modules.regulation.crawler.RegulationCrawlService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * 法规爬虫定时调度内核：跨组织扫描"到期应抓"的追踪源并逐源触发抓取。
 *
 * 位于 kernel（不在 modules 包），选源阶段以系统身份跨全部 org 读取；
 * 实际抓取委托 {@link RegulationCrawlService#crawl}——通过 IsolationContext
 * 临时设为源所属 org，使 OrgScopeAspect 在 crawl 自己的事务里注入正确的
 * visible_orgs，落库仍受 RLS 校验。选源用 TransactionTemplate 而非同类
 * @Transactional 方法（自调用不经代理，事务不会生效）。
 *
 * 设计要点（与 ExpiryScanService 同骨架）：
 *  - 单实例：选源事务内 pg_try_advisory_xact_lock，多实例部署仅一个实例选中；
 *  - 幂等兜底：即使并发重复抓取，RegulationCrawlService 按 dedup_key 去重，不产重复条目；
 *  - 到期判定：enabled 且 (从未抓过 或 距上次抓取超过 frequency 对应间隔
 *    HOURLY=1小时 / DAILY=24小时 / WEEKLY=7天，未知值按 DAILY)；
 *  - 抓取失败不断链：crawl 内部把错误记到源状态（status=ERROR），本轮继续下一源。
 */
@Service
public class ScheduledCrawlService {

    private static final Logger log = LoggerFactory.getLogger(ScheduledCrawlService.class);

    /** 单实例选源锁的 advisory key（本用途专用，与到期扫描 770001 区分）。 */
    private static final long CRAWL_LOCK_KEY = LockKeys.CRAWL;

    @PersistenceContext
    private EntityManager em;

    private final RegulationCrawlService crawlService;
    private final TransactionTemplate tx;

    public ScheduledCrawlService(RegulationCrawlService crawlService, PlatformTransactionManager txManager) {
        this.crawlService = crawlService;
        this.tx = new TransactionTemplate(txManager);
    }

    /**
     * 执行一轮调度：选出到期源 → 逐源以其 org 身份抓取。
     *
     * @return 本轮实际触发抓取的源数量（抢锁失败返回 -1 表示跳过）
     */
    public int runOnce() {
        List<Object[]> due = tx.execute(status -> pickDueSources());
        if (due == null) {
            return -1;
        }
        int triggered = 0;
        for (Object[] r : due) {
            long sourceId = ((Number) r[0]).longValue();
            long orgId = ((Number) r[1]).longValue();
            // 以源所属 org 身份委托抓取：OrgScopeAspect 在 crawl 的新事务里注入 visible_orgs
            IsolationContext.set(List.of(orgId));
            try {
                RegulationCrawlService.CrawlResult res = crawlService.crawl(sourceId, "system-scheduler");
                triggered++;
                if (res.error() != null) {
                    log.warn("定时抓取源 {} 失败：{}", sourceId, res.error());
                } else if (res.added() > 0) {
                    log.info("定时抓取源 {} 命中 {} 条，新增 {} 条", sourceId, res.hit(), res.added());
                }
            } catch (RuntimeException e) {
                // 单源异常不断本轮（源不存在等罕见竞态）
                log.warn("定时抓取源 {} 异常：{}", sourceId, e.getMessage());
            } finally {
                IsolationContext.clear();
            }
        }
        return triggered;
    }

    /** 选源（须在事务内调用）：系统身份跨 org 读取到期源；抢不到 advisory 锁返回 null。 */
    private List<Object[]> pickDueSources() {
        Boolean locked = (Boolean) em.createNativeQuery("SELECT pg_try_advisory_xact_lock(:k)")
                .setParameter("k", CRAWL_LOCK_KEY)
                .getSingleResult();
        if (!Boolean.TRUE.equals(locked)) {
            return null;
        }
        // 架构治理包 A26：会话可见域走 set_config 参数化（防注入样板）
        VisibleOrgsSql.setAllOrgs(em);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                        "SELECT id, org_id FROM regulation_source WHERE enabled = true "
                                + "AND (last_fetched_at IS NULL OR last_fetched_at < now() - "
                                + "(CASE frequency WHEN 'HOURLY' THEN interval '1 hour' "
                                + "WHEN 'WEEKLY' THEN interval '7 days' "
                                + "ELSE interval '24 hours' END)) "
                                + "ORDER BY id")
                .getResultList();
        return new ArrayList<>(rows);
    }
}
