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

    // ---- 安全加固包（B15 失败锁定 / B17 首登改密）----

    /** 连续登录失败次数（成功登录清零）。 */
    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts = 0;

    /** 锁定截止时刻（连续失败 5 次锁 15 分钟；为空=未锁定）。 */
    @Column(name = "locked_until")
    private java.time.OffsetDateTime lockedUntil;

    /** 首登强制改密位（生产初始化应对种子账号置 true）。 */
    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword = false;

    /** 会话版本号（M-15）：登出/改密/踢下线时 +1，使此前签发的令牌 ep 不匹配而失效。 */
    @Column(name = "token_epoch", nullable = false)
    private int tokenEpoch = 0;

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
    public int getFailedAttempts() { return failedAttempts; }
    public java.time.OffsetDateTime getLockedUntil() { return lockedUntil; }
    public boolean isMustChangePassword() { return mustChangePassword; }
    public int getTokenEpoch() { return tokenEpoch; }

    // ---- 安全加固包：登录服务专用变更（包内可见）----
    void recordLoginFailure(int lockThreshold, int lockMinutes) {
        this.failedAttempts++;
        if (this.failedAttempts >= lockThreshold) {
            this.lockedUntil = java.time.OffsetDateTime.now().plusMinutes(lockMinutes);
            this.failedAttempts = 0; // 锁定后计数归零，解锁后重新累计
        }
    }

    void recordLoginSuccess() {
        this.failedAttempts = 0;
        this.lockedUntil = null;
    }

    void changePassword(String newHash) {
        this.passwordHash = newHash;
        this.mustChangePassword = false;
        this.tokenEpoch++;   // M-15：改密使既有会话（旧令牌）失效
    }

    /** 令牌纪元 +1，吊销该用户全部既发 JWT（登出/踢下线，M-15）。 */
    void bumpTokenEpoch() {
        this.tokenEpoch++;
    }
}
