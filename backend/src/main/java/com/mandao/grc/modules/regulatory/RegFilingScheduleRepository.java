package com.mandao.grc.modules.regulatory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 周期性报送计划仓储（M11 B34）。RLS 按 visible_orgs 裁剪。 */
public interface RegFilingScheduleRepository extends JpaRepository<RegFilingSchedule, Long> {

    List<RegFilingSchedule> findAllByOrderByNextDueAsc();
}
