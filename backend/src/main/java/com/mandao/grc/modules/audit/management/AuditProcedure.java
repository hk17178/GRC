package com.mandao.grc.modules.audit.management;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * 审计程序 / 工作底稿（V50 · A2）。
 *
 * 一条程序 = 一项现场实施步骤；执行后记录即工作底稿（workpaper_no 唯一编号 WP-{plan}-{seq}），
 * 再经复核人复核（PENDING → DONE → REVIEWED）。org_id 隔离锚点，RLS 裁剪。
 */
@Entity
@Table(name = "audit_procedure")
public class AuditProcedure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    @Column(name = "plan_id", nullable = false, updatable = false)
    private Long planId;

    @Column(nullable = false)
    private Integer seq;

    @Column(nullable = false, length = 256)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String objective;

    @Column(name = "workpaper_no", nullable = false, length = 32)
    private String workpaperNo;

    @Column(length = 64)
    private String executor;

    @Column(name = "executed_at")
    private OffsetDateTime executedAt;

    /** 执行记录（工作底稿正文）。 */
    @Column(columnDefinition = "TEXT")
    private String result;

    /** PENDING / DONE / REVIEWED。 */
    @Column(nullable = false, length = 12)
    private String status = "PENDING";

    @Column(length = 64)
    private String reviewer;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    protected AuditProcedure() {
    }

    public AuditProcedure(Long orgId, Long planId, int seq, String name, String objective) {
        this.orgId = orgId;
        this.planId = planId;
        this.seq = seq;
        this.name = name;
        this.objective = objective;
        this.workpaperNo = "WP-" + planId + "-" + seq;
    }

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = OffsetDateTime.now();
        }
    }

    /** 执行程序：落执行记录（底稿）与执行人（PENDING → DONE）。 */
    void execute(String result, String executor) {
        this.result = result;
        this.executor = executor;
        this.executedAt = OffsetDateTime.now();
        this.status = "DONE";
    }

    /** 复核底稿（DONE → REVIEWED）。 */
    void review(String reviewer) {
        this.reviewer = reviewer;
        this.reviewedAt = OffsetDateTime.now();
        this.status = "REVIEWED";
    }

    public Long getId() { return id; }
    public Long getOrgId() { return orgId; }
    public Long getPlanId() { return planId; }
    public Integer getSeq() { return seq; }
    public String getName() { return name; }
    public String getObjective() { return objective; }
    public String getWorkpaperNo() { return workpaperNo; }
    public String getExecutor() { return executor; }
    public OffsetDateTime getExecutedAt() { return executedAt; }
    public String getResult() { return result; }
    public String getStatus() { return status; }
    public String getReviewer() { return reviewer; }
    public OffsetDateTime getReviewedAt() { return reviewedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
