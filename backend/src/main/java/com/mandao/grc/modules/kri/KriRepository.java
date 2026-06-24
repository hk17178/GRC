package com.mandao.grc.modules.kri;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * KRI 仓储。不写任何 org 过滤：RLS 在已注入 visible_orgs 的连接上自动裁剪可见行。
 */
public interface KriRepository extends JpaRepository<Kri, Long> {
}
