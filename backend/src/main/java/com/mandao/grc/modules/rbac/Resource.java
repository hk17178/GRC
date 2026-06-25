package com.mandao.grc.modules.rbac;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 权限资源（菜单 + 按钮/动作）。集团级全局字典，不挂 org/RLS。
 * code：菜单=navKey（如 risk）；动作=menu.action（如 risk.create）。
 */
@Entity
@Table(name = "resource")
public class Resource {

    @Id
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private ResourceType type;

    @Column(name = "parent_menu")
    private String parentMenu;

    @Column(nullable = false)
    private Integer sort = 0;

    protected Resource() {
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public ResourceType getType() { return type; }
    public String getParentMenu() { return parentMenu; }
    public Integer getSort() { return sort; }
}
