package com.mandao.grc.modules.regulatory;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 重大事件报送仓储。RLS 自动按 visible_orgs 裁剪，无手写 org 过滤。
 */
public interface MajorIncidentRepository extends JpaRepository<MajorIncidentReport, Long> {
}
