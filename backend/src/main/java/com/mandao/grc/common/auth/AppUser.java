package com.mandao.grc.common.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 平台用户（认证主体）。映射 app_user 表（无 RLS：计算可见域/登录需读取）。
 *
 * 仅承载认证所需字段；用户的角色与可见组织由 user_role_org / VisibleOrgsService 计算。
 */
@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    private Long id;

    @Column(name = "org_id")
    private Long orgId;

    @Column(nullable = false)
    private String username;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "display_name")
    private String displayName;

    @Column(nullable = false)
    private boolean enabled = true;

    protected AppUser() {
    }

    public Long getId() { return id; }
    public Long getOrgId() { return orgId; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getDisplayName() { return displayName; }
    public boolean isEnabled() { return enabled; }
}
