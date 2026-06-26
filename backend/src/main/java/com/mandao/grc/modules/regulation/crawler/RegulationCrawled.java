package com.mandao.grc.modules.regulation.crawler;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 采集到的法规条目。携 org_id 隔离锚点，RLS 裁剪；按 (org, dedup_key) 去重。
 */
@Entity
@Table(name = "regulation_crawled")
public class RegulationCrawled {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Column(name = "dedup_key", nullable = false, length = 256)
    private String dedupKey;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String title;

    @Column(name = "doc_no", length = 128)
    private String docNo;

    @Column(length = 128)
    private String issuer;

    @Column(length = 64)
    private String category;

    @Column(name = "publish_date")
    private LocalDate publishDate;

    @Column(columnDefinition = "TEXT")
    private String url;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "fetched_at")
    private OffsetDateTime fetchedAt;

    protected RegulationCrawled() {
    }

    public RegulationCrawled(Long orgId, Long sourceId, CrawledLaw law) {
        this.orgId = orgId;
        this.sourceId = sourceId;
        this.dedupKey = law.dedupKey();
        this.title = law.title();
        this.docNo = law.docNo();
        this.issuer = law.issuer();
        this.category = law.category();
        this.publishDate = law.publishDate();
        this.url = law.url();
        this.summary = law.summary();
    }

    @PrePersist
    void onCreate() {
        if (this.fetchedAt == null) {
            this.fetchedAt = OffsetDateTime.now();
        }
    }

    public Long getId() { return id; }
    public Long getOrgId() { return orgId; }
    public Long getSourceId() { return sourceId; }
    public String getDedupKey() { return dedupKey; }
    public String getTitle() { return title; }
    public String getDocNo() { return docNo; }
    public String getIssuer() { return issuer; }
    public String getCategory() { return category; }
    public LocalDate getPublishDate() { return publishDate; }
    public String getUrl() { return url; }
    public String getSummary() { return summary; }
    public OffsetDateTime getFetchedAt() { return fetchedAt; }
}
