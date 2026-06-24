package com.mandao.grc.modules.permission;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 角色字典仓储（全局，无 org 过滤——role 非 org-scoped、不启 RLS）。
 */
public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByCode(String code);
}
