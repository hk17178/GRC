package com.mandao.grc.modules.audit.management;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 证书台账仓储（B24）。RLS 按 visible_orgs 裁剪。 */
public interface CertificateRepository extends JpaRepository<Certificate, Long> {

    List<Certificate> findAllByOrderByExpiryDateAsc();
}
