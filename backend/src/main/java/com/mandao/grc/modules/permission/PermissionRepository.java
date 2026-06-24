package com.mandao.grc.modules.permission;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 权限点字典仓储（全局，无 org 过滤——permission 非 org-scoped、不启 RLS）。
 */
public interface PermissionRepository extends JpaRepository<Permission, Long> {

    Optional<Permission> findByCode(String code);
}
