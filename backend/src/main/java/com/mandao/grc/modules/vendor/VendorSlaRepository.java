package com.mandao.grc.modules.vendor;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 供应商 SLA 仓储（RLS 裁剪）。 */
public interface VendorSlaRepository extends JpaRepository<VendorSla, Long> {

    List<VendorSla> findByVendorIdOrderByIdAsc(Long vendorId);

    List<VendorSla> findAllByOrderByDueDateAsc();
}
