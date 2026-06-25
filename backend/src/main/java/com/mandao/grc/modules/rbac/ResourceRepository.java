package com.mandao.grc.modules.rbac;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 资源字典仓库（全局，无 RLS）。 */
public interface ResourceRepository extends JpaRepository<Resource, Long> {
    List<Resource> findAllByOrderBySortAsc();
}
