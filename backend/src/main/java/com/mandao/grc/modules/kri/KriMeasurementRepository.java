package com.mandao.grc.modules.kri;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * KRI 测量仓储。不写任何 org 过滤：RLS 自动裁剪可见行。
 */
public interface KriMeasurementRepository extends JpaRepository<KriMeasurement, Long> {

    /** 列出某 KRI 的测量历史（最新在前）。 */
    List<KriMeasurement> findByKriIdOrderByIdDesc(Long kriId);
}
