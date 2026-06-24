package com.mandao.grc.modules.permission;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 权限点字典（M8 RBAC，全局非 org-scoped），映射 V7 全局 permission 表。
 *
 * 权限为全平台统一字典（不含 org_id、不启 RLS），通过 {@link RolePermission} 关联到角色。
 * 主键 BIGSERIAL（V7 建），用 {@link GenerationType#IDENTITY}。
 *
 * 设计依据：需求文档 M8 权限审批（RBAC、权限点）、D2-5。
 */
@Entity
@Table(name = "permission")
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 权限点业务码（全局唯一）。 */
    @Column(nullable = false, length = 64, unique = true)
    private String code;

    /** 权限点显示名。 */
    @Column(nullable = false, length = 128)
    private String name;

    protected Permission() {
    }

    public Permission(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
}
