package com.mandao.grc.modules.regulation.crawler;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * 法规追踪源（可配置）。携 org_id 隔离锚点，RLS 按 visible_orgs 裁剪。
 *
 * SAMPLE 源无需 url/config；HTTP 源用 url（列表页）+ config（CSS 选择器 JSON）抓取。
 */
@Entity
@Table(name = "regulation_source")
public class RegulationSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    @Column(nullable = false, length = 128)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 16)
    private SourceType sourceType = SourceType.SAMPLE;

    @Column(columnDefinition = "TEXT")
    private String url;

    /** HTTP 源的 CSS 选择器配置（JSON）。 */
    @Column(columnDefinition = "TEXT")
    private String config;

    @Column(nullable = false, length = 16)
    private String frequency = "DAILY";

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(nullable = false, length = 16)
    private String status = "OK";

    @Column(name = "last_fetched_at")
    private OffsetDateTime lastFetchedAt;

    @Column(name = "last_hit_count", nullable = false)
    private int lastHitCount = 0;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    protected RegulationSource() {
    }

    public RegulationSource(Long orgId, String name, SourceType sourceType, String url, String config, String frequency) {
        this.orgId = orgId;
        this.name = name;
        this.sourceType = sourceType == null ? SourceType.SAMPLE : sourceType;
        this.url = url;
        this.config = config;
        this.frequency = frequency == null ? "DAILY" : frequency;
    }

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = OffsetDateTime.now();
        }
    }

    /** 记录一次抓取结果。 */
    public void markFetched(int hitCount, String error) {
        this.lastFetchedAt = OffsetDateTime.now();
        this.lastHitCount = hitCount;
        this.lastError = error;
        this.status = error == null ? "OK" : "ERROR";
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Long getId() { return id; }
    public Long getOrgId() { return orgId; }
    public String getName() { return name; }
    public SourceType getSourceType() { return sourceType; }
    public String getUrl() { return url; }
    public String getConfig() { return config; }
    public String getFrequency() { return frequency; }
    public boolean isEnabled() { return enabled; }
    public String getStatus() { return status; }
    public OffsetDateTime getLastFetchedAt() { return lastFetchedAt; }
    public int getLastHitCount() { return lastHitCount; }
    public String getLastError() { return lastError; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
