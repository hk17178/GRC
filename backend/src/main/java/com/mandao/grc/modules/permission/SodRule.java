package com.mandao.grc.modules.permission;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * SoD 职责分离互斥规则（M8 红线，全局字典），映射 V7 全局 sod_rule 表。
 *
 * 一条规则声明一对互斥角色（roleA / roleB）：同一用户在同一组织内不得同时持有二者——
 * 除非该 user 在该 org 持有针对本规则的有效 {@link SodException}（经审批的豁免）。
 * 互斥判定与豁免放行由 {@link PermissionService#grantRole} 强制（违反抛 {@link SodViolationException}）。
 *
 * 全局字典（不含 org_id、不启 RLS）；主键 BIGSERIAL（V7 建），用 {@link GenerationType#IDENTITY}。
 *
 * 设计依据：需求文档 M8 权限审批（SoD）、D1-3 §4.7、D2-5。
 */
@Entity
@Table(name = "sod_rule")
public class SodRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 互斥角色对之一。 */
    @Column(name = "role_a_id", nullable = false)
    private Long roleAId;

    /** 互斥角色对之二。 */
    @Column(name = "role_b_id", nullable = false)
    private Long roleBId;

    /** 规则说明。 */
    @Column(length = 256)
    private String description;

    protected SodRule() {
    }

    public SodRule(Long roleAId, Long roleBId, String description) {
        this.roleAId = roleAId;
        this.roleBId = roleBId;
        this.description = description;
    }

    public Long getId() { return id; }
    public Long getRoleAId() { return roleAId; }
    public Long getRoleBId() { return roleBId; }
    public String getDescription() { return description; }

    /**
     * 给定本规则中的一个角色，返回与之互斥的另一个角色 id；若入参不属于本规则则返回 null。
     */
    public Long counterpartOf(Long roleId) {
        if (roleAId.equals(roleId)) {
            return roleBId;
        }
        if (roleBId.equals(roleId)) {
            return roleAId;
        }
        return null;
    }
}
