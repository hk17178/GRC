package com.mandao.grc.modules.regulatory;

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
 * 周期性报送计划（M11 B34），映射 V64 reg_filing_schedule 表。
 *
 * 面向月/季/年重复的法定报送（如季度反洗钱报表、年度等保报告）：定义一次周期与锚点，
 * 由内核到期扫描在到期前 lead_days 天自动生成一份 {@link RegFiling} 草稿实例并推进 next_due，
 * 避免"每期手工新建、漏建即漏报"。org_id 隔离锚点，RLS 裁剪。
 */
@Entity
@Table(name = "reg_filing_schedule")
public class RegFilingSchedule {

    /** 报送周期。 */
    public enum Period { MONTHLY, QUARTERLY, ANNUAL }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    @Column(nullable = false, length = 256)
    private String title;

    @Column(length = 64)
    private String regulator;

    /** 周期枚举名（MONTHLY/QUARTERLY/ANNUAL），以字符串持久化。 */
    @Column(nullable = false, length = 16)
    private String period;

    @Column(name = "lead_days", nullable = false)
    private int leadDays = 15;

    @Column(name = "next_due", nullable = false)
    private LocalDate nextDue;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "last_generated")
    private LocalDate lastGenerated;

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    protected RegFilingSchedule() {
    }

    public RegFilingSchedule(Long orgId, String title, String regulator, Period period,
                             int leadDays, LocalDate nextDue, String createdBy) {
        this.orgId = orgId;
        this.title = title;
        this.regulator = regulator;
        this.period = period.name();
        this.leadDays = leadDays <= 0 ? 15 : leadDays;
        this.nextDue = nextDue;
        this.createdBy = createdBy;
        this.enabled = true;
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

    /** 按周期推进到下一个到期日。 */
    public LocalDate advance(LocalDate from) {
        return switch (Period.valueOf(period)) {
            case MONTHLY -> from.plusMonths(1);
            case QUARTERLY -> from.plusMonths(3);
            case ANNUAL -> from.plusYears(1);
        };
    }

    public Long getId() { return id; }
    public Long getOrgId() { return orgId; }
    public String getTitle() { return title; }
    public String getRegulator() { return regulator; }
    public String getPeriod() { return period; }
    public int getLeadDays() { return leadDays; }
    public LocalDate getNextDue() { return nextDue; }
    public boolean isEnabled() { return enabled; }
    public LocalDate getLastGenerated() { return lastGenerated; }
    public String getCreatedBy() { return createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    void setEnabled(boolean enabled) { this.enabled = enabled; }
    void setNextDue(LocalDate nextDue) { this.nextDue = nextDue; }
    void setLastGenerated(LocalDate lastGenerated) { this.lastGenerated = lastGenerated; }
}
