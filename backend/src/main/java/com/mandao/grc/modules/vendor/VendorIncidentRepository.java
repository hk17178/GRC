package com.mandao.grc.modules.vendor;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 供应商事件仓储（RLS 裁剪）。 */
public interface VendorIncidentRepository extends JpaRepository<VendorIncident, Long> {

    List<VendorIncident> findAllByOrderByIdDesc();
}
