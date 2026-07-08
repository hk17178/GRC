package com.mandao.grc.modules.workflow;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 流程发起快照仓储（D1-8 H-06 接线）。RLS 按 visible_orgs 裁剪。 */
public interface ProcessLaunchRepository extends JpaRepository<ProcessLaunch, Long> {

    /** 某单据的发起快照（按发起时间倒序，最新一条即当前在途单据固化的流程版本）。 */
    List<ProcessLaunch> findByBizTypeAndBizIdOrderByIdDesc(String bizType, Long bizId);
}
