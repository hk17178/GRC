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
 * 一条规则声明一对互斥角色（roleA / roleB）：同一用户在同一组织内不得同时持有二者。
 * 红线语义由 enforceMode 决定（D1-3 §4.7·补 D6）：
 *   DETECT（默认，检测型）：授予互斥角色【不阻断】、并集照常生效，仅登记冲突待例外审批；
 *   BLOCK（阻断型）：该 user 在该 org 无有效 {@link SodException} 时【硬阻断】（抛 {@link SodViolationException}）。
 * 互斥判定/阻断/冲突登记由 {@link PermissionService#grantRole} 强制。
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

    /** 红线强制模式：DETECT（检测型·放行并登记冲突，默认） | BLOCK（阻断型·无豁免则拒绝）。 */
    @Column(name = "enforce_mode", nullable = false, length = 8)
    private String enforceMode = "DETECT";

    /** 规则说明。 */
    @Column(length = 256)
    private String description;

    protected SodRule() {
    }

    public SodRule(Long roleAId, Long roleBId, String enforceMode, String description) {
        this.roleAId = roleAId;
        this.roleBId = roleBId;
        this.enforceMode = enforceMode;
        this.description = description;
    }

    public Long getId() { return id; }
    public Long getRoleAId() { return roleAId; }
    public Long getRoleBId() { return roleBId; }
    public String getEnforceMode() { return enforceMode; }
    public String getDescription() { return description; }

    /** 是否为硬阻断型（BLOCK）规则。 */
    public boolean isBlock() { return "BLOCK".equals(enforceMode); }

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
