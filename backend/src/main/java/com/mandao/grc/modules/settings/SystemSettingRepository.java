package com.mandao.grc.modules.settings;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 系统设置仓储。不写 org 过滤：RLS 自动裁剪可见行（各租户读到自己的配置）。 */
public interface SystemSettingRepository extends JpaRepository<SystemSetting, Long> {

    /** 按分组列出配置项。 */
    List<SystemSetting> findByCategory(String category);
}
