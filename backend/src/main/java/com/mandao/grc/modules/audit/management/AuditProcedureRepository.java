package com.mandao.grc.modules.audit.management;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 审计程序/底稿仓储（RLS 裁剪）。 */
public interface AuditProcedureRepository extends JpaRepository<AuditProcedure, Long> {

    List<AuditProcedure> findByPlanIdOrderBySeqAsc(Long planId);

    /** 计划内最大序号（新增程序时递增编号用；无程序返回 null）。 */
    @org.springframework.data.jpa.repository.Query(
            "select max(p.seq) from AuditProcedure p where p.planId = :planId")
    Integer maxSeq(Long planId);
}
