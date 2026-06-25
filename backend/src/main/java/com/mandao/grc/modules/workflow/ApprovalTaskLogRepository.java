package com.mandao.grc.modules.workflow;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 审批决定流水仓库。隔离由 RLS 兜底。 */
public interface ApprovalTaskLogRepository extends JpaRepository<ApprovalTaskLog, Long> {

    /** 列出某实例的决定流水（时间升序）。 */
    List<ApprovalTaskLog> findByInstanceIdOrderByIdAsc(Long instanceId);
}
