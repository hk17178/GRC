package com.mandao.grc.modules.audit.management;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 整改工单（M3：审计发现的整改任务，构成 发现→整改→验证 闭环）。
 *
 * 携带 org_id 隔离锚点（与所属发现同组织）；可见性/可写性由 RLS 自动裁剪。
 * 一个审计发现可派多条整改工单；工单经 PENDING→IN_PROGRESS→SUBMITTED→VERIFIED 推进，
 * 验证不通过退回 IN_PROGRESS。发现须有 ≥1 条 VERIFIED 工单方可标记为已整改（验证闭环红线）。
 */
@Entity
@Table(name = "remediation_order")
public class RemediationOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 隔离锚点：所属组织。 */
    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    /** 所属审计发现。 */
    @Column(name = "finding_id", nullable = false, updatable = false)
    private Long findingId;

    /** 整改责任人。 */
    @Column(length = 64)
    private String assignee;

    /** 整改期限。 */
    @Column(name = "due_date")
    private LocalDate dueDate;

    /** 整改措施。 */
    @Column(columnDefinition = "TEXT")
    private String measure;

    /** 状态机当前态。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RemediationStatus status = RemediationStatus.PENDING;

    /** 整改证据/说明（提交时填写）。 */
    @Column(columnDefinition = "TEXT")
    private String evidence;

    /** 验证人（验证通过时落定）。 */
    @Column(length = 64)
    private String verifier;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    /** JPA 要求的无参构造。 */
    protected RemediationOrder() {
    }

    /** 业务构造：派一条 PENDING 整改工单。 */
    public RemediationOrder(Long orgId, Long findingId, String assignee, LocalDate dueDate, String measure) {
        this.orgId = orgId;
        this.findingId = findingId;
        this.assignee = assignee;
        this.dueDate = dueDate;
        this.measure = measure;
        this.status = RemediationStatus.PENDING;
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

    // 以下状态变更由 Service 在校验当前态后调用。

    /** PENDING → IN_PROGRESS。 */
    void start() {
        this.status = RemediationStatus.IN_PROGRESS;
    }

    /** IN_PROGRESS → SUBMITTED（填写整改证据）。 */
    void submit(String evidence) {
        this.status = RemediationStatus.SUBMITTED;
        this.evidence = evidence;
    }

    /** SUBMITTED → VERIFIED（落定验证人）。 */
    void verify(String verifier) {
        this.status = RemediationStatus.VERIFIED;
        this.verifier = verifier;
    }

    /** SUBMITTED → IN_PROGRESS（验证不通过，退回返工）。 */
    void reject() {
        this.status = RemediationStatus.IN_PROGRESS;
    }

    public Long getId() { return id; }
    public Long getOrgId() { return orgId; }
    public Long getFindingId() { return findingId; }
    public String getAssignee() { return assignee; }
    public LocalDate getDueDate() { return dueDate; }
    public String getMeasure() { return measure; }
    public RemediationStatus getStatus() { return status; }
    public String getEvidence() { return evidence; }
    public String getVerifier() { return verifier; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
