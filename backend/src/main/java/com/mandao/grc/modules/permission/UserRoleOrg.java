package com.mandao.grc.modules.permission;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * 权限四元组核心实体（M8）：org × user × role × active，映射 V7 org-scoped 表 user_role_org。
 *
 * 授权即新增/激活一行（active=true）；回收置 active=false（软删，保留可追溯），不物理删除。
 * 同一 (org_id, user_id, role_id) 至多一行（V7 UNIQUE）。
 *
 * 隔离锚点 org_id；可见/可写由 RLS 依据 app.visible_orgs 自动裁剪（USING/WITH CHECK，V7 已建）。
 * 主键 BIGSERIAL（V7 建），用 {@link GenerationType#IDENTITY}。
 *
 * 设计依据：需求文档 M8 权限审批（权限四元组、UAR、SoD）、D1-3 §4.7、D2-5。
 */
@Entity
@Table(name = "user_role_org")
public class UserRoleOrg {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 隔离锚点：授权所在组织。 */
    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    /** 被授权用户（V1 既有 app_user.id）。 */
    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    /** 角色（全局 role.id）。 */
    @Column(name = "role_id", nullable = false, updatable = false)
    private Long roleId;

    /** 授权人 actor。 */
    @Column(name = "granted_by", length = 64)
    private String grantedBy;

    @Column(name = "granted_at", nullable = false)
    private OffsetDateTime grantedAt;

    /** 是否有效；回收或被 UAR 撤销置 false。 */
    @Column(nullable = false)
    private boolean active = true;

    protected UserRoleOrg() {
    }

    public UserRoleOrg(Long orgId, Long userId, Long roleId, String grantedBy) {
        this.orgId = orgId;
        this.userId = userId;
        this.roleId = roleId;
        this.grantedBy = grantedBy;
        this.active = true;
    }

    @PrePersist
    void onCreate() {
        if (this.grantedAt == null) {
            this.grantedAt = OffsetDateTime.now();
        }
    }

    public Long getId() { return id; }
    public Long getOrgId() { return orgId; }
    public Long getUserId() { return userId; }
    public Long getRoleId() { return roleId; }
    public String getGrantedBy() { return grantedBy; }
    public OffsetDateTime getGrantedAt() { return grantedAt; }
    public boolean isActive() { return active; }

    // 包级可见：仅由 Service 在校验后调用。
    void setActive(boolean active) { this.active = active; }
    void setGrantedBy(String grantedBy) { this.grantedBy = grantedBy; }
    void setGrantedAt(OffsetDateTime grantedAt) { this.grantedAt = grantedAt; }
}
