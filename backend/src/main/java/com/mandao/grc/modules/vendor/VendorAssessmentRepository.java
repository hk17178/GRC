package com.mandao.grc.modules.vendor;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 供应商评估仓储。不写 org 过滤：RLS 自动裁剪可见行。 */
public interface VendorAssessmentRepository extends JpaRepository<VendorAssessment, Long> {

    /** 列出某供应商的评估历史（新→旧）。 */
    List<VendorAssessment> findByVendorIdOrderByIdDesc(Long vendorId);

    /** 准入门控判定：该供应商是否已有评估。 */
    boolean existsByVendorId(Long vendorId);
}
