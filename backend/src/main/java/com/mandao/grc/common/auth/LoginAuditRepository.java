package com.mandao.grc.common.auth;

import org.springframework.data.jpa.repository.JpaRepository;

/** 登录审计仓储（安全加固包 B15）。 */
public interface LoginAuditRepository extends JpaRepository<LoginAudit, Long> {

    java.util.List<LoginAudit> findTop100ByOrderByIdDesc();
}
