package com.mandao.grc.modules.obligation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 义务举证链仓储（RLS 按 visible_orgs 裁剪）。 */
public interface ObligationLinkRepository extends JpaRepository<ObligationLink, Long> {

    List<ObligationLink> findByObligationIdOrderByIdAsc(Long obligationId);

    /** 派生状态用：一次取全部义务的链（批量，避免列表页 N+1）。 */
    List<ObligationLink> findByObligationIdIn(List<Long> obligationIds);

    boolean existsByObligationIdAndRefTypeAndRefId(Long obligationId, String refType, Long refId);
}
