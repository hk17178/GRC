package com.mandao.grc.modules.assessment;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * 一次性签名令牌（V57 · 手机扫码签名）。
 *
 * token 即凭证（随机 UUID，5 分钟过期，取回即 USED）；平台表不挂 RLS——
 * 手机免登录页仅凭 token 存取本行，不触达任何 RLS 业务表。
 */
@Entity
@Table(name = "signature_ticket")
public class SignatureTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64, unique = true)
    private String token;

    @Column(name = "assessment_id", nullable = false, updatable = false)
    private Long assessmentId;

    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    @Column(length = 256)
    private String title;

    /** PENDING / SIGNED / USED / EXPIRED。 */
    @Column(nullable = false, length = 12)
    private String status = "PENDING";

    @JsonIgnore
    @Column
    private byte[] signature;

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    protected SignatureTicket() {
    }

    public SignatureTicket(String token, Long assessmentId, Long orgId, String title,
                           String createdBy, OffsetDateTime expiresAt) {
        this.token = token;
        this.assessmentId = assessmentId;
        this.orgId = orgId;
        this.title = title;
        this.createdBy = createdBy;
        this.expiresAt = expiresAt;
    }

    public boolean expired() {
        return OffsetDateTime.now().isAfter(expiresAt);
    }

    void sign(byte[] signature) {
        this.signature = signature;
        this.status = "SIGNED";
    }

    void setStatus(String status) {
        this.status = status;
    }

    public Long getId() { return id; }
    public String getToken() { return token; }
    public Long getAssessmentId() { return assessmentId; }
    public Long getOrgId() { return orgId; }
    public String getTitle() { return title; }
    public String getStatus() { return status; }
    public byte[] getSignature() { return signature; }
    public String getCreatedBy() { return createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
}
