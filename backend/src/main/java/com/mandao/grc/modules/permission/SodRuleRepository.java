package com.mandao.grc.modules.permission;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * SoD 互斥规则仓储（全局字典，无 org 过滤——sod_rule 非 org-scoped、不启 RLS）。
 */
public interface SodRuleRepository extends JpaRepository<SodRule, Long> {

    /** 取涉及某角色的全部互斥规则（作为 roleA 或 roleB）。 */
    List<SodRule> findByRoleAIdOrRoleBId(Long roleAId, Long roleBId);
}
