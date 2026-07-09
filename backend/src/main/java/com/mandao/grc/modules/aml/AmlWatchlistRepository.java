package com.mandao.grc.modules.aml;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** AML 名单仓储。不写 org 过滤：RLS 自动裁剪可见行。 */
public interface AmlWatchlistRepository extends JpaRepository<AmlWatchlist, Long> {

    /** 全部名单条目（最新在前，含 RETIRED 供台账展示）。 */
    List<AmlWatchlist> findByOrderByIdDesc();

    /** 按状态过滤（筛查只针对 ACTIVE 生效条目）。 */
    List<AmlWatchlist> findByStatusOrderByIdDesc(String status);
}
