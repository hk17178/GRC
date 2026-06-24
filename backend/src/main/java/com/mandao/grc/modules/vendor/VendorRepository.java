package com.mandao.grc.modules.vendor;

import org.springframework.data.jpa.repository.JpaRepository;

/** 供应商仓储。不写 org 过滤：RLS 自动裁剪可见行。 */
public interface VendorRepository extends JpaRepository<Vendor, Long> {
}
