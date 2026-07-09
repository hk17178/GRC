package com.mandao.grc.modules.kri;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * KRI 仓储。不写任何 org 过滤：RLS 在已注入 visible_orgs 的连接上自动裁剪可见行。
 */
public interface KriRepository extends JpaRepository<Kri, Long> {

    /** 按 (组织, 编码) 解析 KRI（B39 外部推送按 code 定位；RLS 仍兜底裁剪不可见组织）。 */
    Optional<Kri> findByOrgIdAndCode(Long orgId, String code);
}
