package com.mandao.grc.modules.audit.management;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 证书有效期台账（收口批 B24 / M3-13），映射 V67 certificate 表。
 *
 * 外审页「认证有效期临近」的真值来源：登记各认证证书的到期日，由内核到期扫描在到期前
 * 60/30/7 天产 CERT_EXPIRY 提醒。org_id 隔离锚点，RLS 裁剪。
 */
@Entity
@Table(name = "certificate")
public class Certificate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    @Column(nullable = false, length = 256)
    private String name;

    @Column(length = 32)
    private String framework;

    @Column(name = "cert_no", length = 128)
    private String certNo;

    @Column(length = 128)
    private String issuer;

    @Column(name = "issued_date")
    private LocalDate issuedDate;

    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    @Column(nullable = false, length = 16)
    private String status = "VALID";

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    protected Certificate() {
    }

    public Certificate(Long orgId, String name, String framework, String certNo, String issuer,
                       LocalDate issuedDate, LocalDate expiryDate, String createdBy) {
        this.orgId = orgId;
        this.name = name;
        this.framework = framework;
        this.certNo = certNo;
        this.issuer = issuer;
        this.issuedDate = issuedDate;
        this.expiryDate = expiryDate;
        this.createdBy = createdBy;
        this.status = "VALID";
    }

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public Long getOrgId() { return orgId; }
    public String getName() { return name; }
    public String getFramework() { return framework; }
    public String getCertNo() { return certNo; }
    public String getIssuer() { return issuer; }
    public LocalDate getIssuedDate() { return issuedDate; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public String getStatus() { return status; }
    public String getCreatedBy() { return createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    void setStatus(String status) { this.status = status; }
    void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }
}
