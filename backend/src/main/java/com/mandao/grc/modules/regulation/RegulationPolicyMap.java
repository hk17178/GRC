package com.mandao.grc.modules.regulation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * 法规-制度映射（需求 6.2：法规条款命中制度，支撑"法规-制度映射概览"与变更核查）。
 * 携 org_id，RLS 裁剪；(regulation, policy, clause) 唯一防重。
 */
@Entity
@Table(name = "regulation_policy_map")
public class RegulationPolicyMap {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    @Column(name = "regulation_id", nullable = false)
    private Long regulationId;

    @Column(name = "policy_id", nullable = false)
    private Long policyId;

    /** 法规条款（如 §41）。 */
    @Column(length = 128)
    private String clause;

    /** 映射说明。 */
    @Column(length = 256)
    private String note;

    // ---- AI 符合度评估（六轮 #6）----

    /** 评估结论：符合 / 部分符合 / 不符合 / 待复核（模型输出无法归类时）。 */
    @Column(name = "assess_verdict", length = 16)
    private String assessVerdict;

    /** 评估详情（差距说明 + 建议修订点，模型原文）。 */
    @Column(name = "assess_detail", columnDefinition = "TEXT")
    private String assessDetail;

    /** 评估时间。 */
    @Column(name = "assessed_at")
    private OffsetDateTime assessedAt;

    /** 法规再变更后置 true（需重评标记），重评后清除。 */
    @Column(name = "assess_stale", nullable = false)
    private boolean assessStale = false;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    protected RegulationPolicyMap() {
    }

    public RegulationPolicyMap(Long orgId, Long regulationId, Long policyId, String clause, String note) {
        this.orgId = orgId;
        this.regulationId = regulationId;
        this.policyId = policyId;
        this.clause = clause;
        this.note = note;
    }

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = OffsetDateTime.now();
        }
    }

    public Long getId() { return id; }
    public Long getOrgId() { return orgId; }
    public Long getRegulationId() { return regulationId; }
    public Long getPolicyId() { return policyId; }
    public String getClause() { return clause; }
    public String getNote() { return note; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public String getAssessVerdict() { return assessVerdict; }
    public String getAssessDetail() { return assessDetail; }
    public OffsetDateTime getAssessedAt() { return assessedAt; }
    public boolean isAssessStale() { return assessStale; }

    /** 写入 AI 符合度评估结果（由 Service 调用），并清除需重评标记。 */
    void recordAssessment(String verdict, String detail) {
        this.assessVerdict = verdict;
        this.assessDetail = detail;
        this.assessedAt = OffsetDateTime.now();
        this.assessStale = false;
    }

    /** 法规变更后标记需重评（由 Service 调用）。 */
    void markStale() {
        this.assessStale = true;
    }
}
