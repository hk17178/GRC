package com.mandao.grc.modules.aml;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * 反洗钱名单条目（AML 名单管理），映射 V80 aml_watchlist。
 *
 * 制裁名单 SANCTION / 政治敏感人物 PEP / 内部黑名单 INTERNAL；供客户与交易对手筛查。
 * 携 org_id 隔离锚点，RLS 按 app.visible_orgs 裁剪——筛查只在本组织可见域内。
 */
@Entity
@Table(name = "aml_watchlist")
public class AmlWatchlist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    @Column(name = "list_type", nullable = false, length = 16)
    private String listType;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(name = "id_number", length = 64)
    private String idNumber;

    @Column(length = 64)
    private String country;

    @Column(length = 128)
    private String source;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(nullable = false, length = 16)
    private String status = "ACTIVE";

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    protected AmlWatchlist() {
    }

    public AmlWatchlist(Long orgId, String listType, String name, String idNumber,
                        String country, String source, String reason, String createdBy) {
        this.orgId = orgId;
        this.listType = listType;
        this.name = name;
        this.idNumber = idNumber;
        this.country = country;
        this.source = source;
        this.reason = reason;
        this.createdBy = createdBy;
        this.status = "ACTIVE";
    }

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = OffsetDateTime.now();
        }
    }

    public Long getId() { return id; }
    public Long getOrgId() { return orgId; }
    public String getListType() { return listType; }
    public String getName() { return name; }
    public String getIdNumber() { return idNumber; }
    public String getCountry() { return country; }
    public String getSource() { return source; }
    public String getReason() { return reason; }
    public String getStatus() { return status; }
    public String getCreatedBy() { return createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    void retire() { this.status = "RETIRED"; }
}
