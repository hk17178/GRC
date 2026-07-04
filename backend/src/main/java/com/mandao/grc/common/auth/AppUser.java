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

    // ---- CR-003 身份数据模型先行（八轮 8-6/B11：真实 AD 对接留联测，结构先立避免后期迁移主键）----

    /** 身份来源：LOCAL 本地账号 / AD 域账号（联测接入）。 */
    @Column(name = "identity_source", nullable = false, length = 16)
    private String identitySource = "LOCAL";

    /** AD 域标识（本地账号为空；域内 (domain_id, username) 唯一）。 */
    @Column(name = "domain_id")
    private Long domainId;

    /** 平台侧独立禁用位：与源目录(AD)的启停解耦——域账号在 AD 有效但平台可单独停用。 */
    @Column(name = "platform_disabled", nullable = false)
    private boolean platformDisabled = false;

    protected AppUser() {
    }

    public Long getId() { return id; }
    public Long getOrgId() { return orgId; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getDisplayName() { return displayName; }
    public boolean isEnabled() { return enabled; }
    public String getIdentitySource() { return identitySource; }
    public Long getDomainId() { return domainId; }
    public boolean isPlatformDisabled() { return platformDisabled; }
}
