package com.mandao.grc.modules.assessment;

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
 * 评估计划（需求 4.2.1：年度/季度周期性计划与临时专项评估的创建/排期/状态跟踪）。
 *
 * 生命周期：PLANNED（排期）→ STARTED（启动，生成关联评估）→ DONE（完成）。
 * 携 org_id，RLS 裁剪。
 */
@Entity
@Table(name = "assessment_plan")
public class AssessmentPlan {

    public static final String PLANNED = "PLANNED";
    public static final String STARTED = "STARTED";
    public static final String DONE = "DONE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    @Column(nullable = false, length = 256)
    private String title;

    /** 周期类型：ANNUAL 年度 / QUARTERLY 季度 / ADHOC 临时专项。 */
    @Column(name = "period_type", nullable = false, length = 16)
    private String periodType = "ANNUAL";

    /** 计划开始日期。 */
    @Column(name = "planned_date")
    private LocalDate plannedDate;

    /** 关联评估模板（启动时带入表单引擎，可空）。 */
    @Column(name = "template_id")
    private Long templateId;

    @Column(nullable = false, length = 16)
    private String status = PLANNED;

    /** 启动后生成的评估 id。 */
    @Column(name = "assessment_id")
    private Long assessmentId;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    protected AssessmentPlan() {
    }

    public AssessmentPlan(Long orgId, String title, String periodType, LocalDate plannedDate, Long templateId) {
        this.orgId = orgId;
        this.title = title;
        this.periodType = periodType == null ? "ANNUAL" : periodType;
        this.plannedDate = plannedDate;
        this.templateId = templateId;
    }

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = OffsetDateTime.now();
        }
    }

    /** 启动：记录生成的评估并转 STARTED。 */
    public void start(Long assessmentId) {
        this.assessmentId = assessmentId;
        this.status = STARTED;
    }

    public void markDone() {
        this.status = DONE;
    }

    public Long getId() { return id; }
    public Long getOrgId() { return orgId; }
    public String getTitle() { return title; }
    public String getPeriodType() { return periodType; }
    public LocalDate getPlannedDate() { return plannedDate; }
    public Long getTemplateId() { return templateId; }
    public String getStatus() { return status; }
    public Long getAssessmentId() { return assessmentId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
