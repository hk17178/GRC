package com.mandao.grc.modules.control;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 控制项仓储。不写任何 org 过滤：RLS 在已注入 visible_orgs 的连接上自动裁剪可见行。
 */
public interface ControlRepository extends JpaRepository<Control, Long> {
}
