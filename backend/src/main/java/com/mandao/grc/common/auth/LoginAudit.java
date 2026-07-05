package com.mandao.grc.common.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * 登录审计台账（安全加固包 B15）：成功/失败/锁定拒绝全记录（等保三级测评必查）。
 * 平台级表无 RLS——审计线不受可见域裁剪；查询入口在看板与留痕页（后续接线）。
 */
@Entity
@Table(name = "login_audit")
public class LoginAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String username;

    @Column(nullable = false)
    private boolean success;

    /** BAD_CREDENTIAL / LOCKED / PLATFORM_DISABLED / OK。 */
    @Column(length = 64)
    private String reason;

    @Column(name = "client_ip", length = 64)
    private String clientIp;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    protected LoginAudit() {
    }

    public LoginAudit(String username, boolean success, String reason, String clientIp) {
        this.username = username;
        this.success = success;
        this.reason = reason;
        this.clientIp = clientIp;
    }

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = OffsetDateTime.now();
        }
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public boolean isSuccess() { return success; }
    public String getReason() { return reason; }
    public String getClientIp() { return clientIp; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
