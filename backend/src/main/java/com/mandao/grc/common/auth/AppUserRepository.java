package com.mandao.grc.common.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** 用户仓库（认证用）。app_user 无 RLS，可直接按用户名查。 */
public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsername(String username);
}
