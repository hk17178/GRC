package com.mandao.grc.modules.aml;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** STR 可疑交易报告仓储。不写 org 过滤：RLS 自动裁剪可见行。 */
public interface StrReportRepository extends JpaRepository<StrReport, Long> {

    /** 全部 STR（最新在前）。 */
    List<StrReport> findByOrderByIdDesc();
}
