package com.mandao.grc.modules.control;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 控制项-框架映射仓储。不写任何 org 过滤：RLS 自动裁剪可见行。
 */
public interface ControlFrameworkRefRepository extends JpaRepository<ControlFrameworkRef, Long> {

    /** 列出某控制项的全部框架映射。 */
    List<ControlFrameworkRef> findByControlId(Long controlId);

    /** 判重：同一控制项是否已存在同框架同条款的映射。 */
    boolean existsByControlIdAndFrameworkAndClause(Long controlId, ControlFramework framework, String clause);
}
