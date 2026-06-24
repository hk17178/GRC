package com.mandao.grc.modules.assessment;

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

import java.time.OffsetDateTime;

/**
 * 评估项（一次评估下的单个控制点，"评估-控件复用"的落地载体）。
 *
 * 携带 org_id 隔离锚点（与所属评估同组织）；可见性/可写性由 RLS 自动裁剪。
 * 由模板实例化时从 {@link AssessmentTemplateItem} 拷贝而来（含 control_id 引用），
 * 评估过程中对其逐项给出符合性结论 {@link AssessmentItemResult}。
 */
@Entity
@Table(name = "assessment_item")
public class AssessmentItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 隔离锚点：所属组织。 */
    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    /** 所属评估。 */
    @Column(name = "assessment_id", nullable = false, updatable = false)
    private Long assessmentId;

    /** 排序序号。 */
    @Column(nullable = false)
    private Integer seq;

    /** 引用的统一控制项 id（可空）。 */
    @Column(name = "control_id")
    private Long controlId;

    /** 框架条款编号。 */
    @Column(length = 64)
    private String clause;

    /** 检查要求/说明。 */
    @Column(columnDefinition = "TEXT")
    private String requirement;

    /** 评估结论。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AssessmentItemResult result = AssessmentItemResult.PENDING;

    /** 评估说明/证据。 */
    @Column(columnDefinition = "TEXT")
    private String conclusion;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    /** JPA 要求的无参构造。 */
    protected AssessmentItem() {
    }

    /** 业务构造：实例化时以 PENDING 态新建评估项。 */
    public AssessmentItem(Long orgId, Long assessmentId, Integer seq, Long controlId,
                          String clause, String requirement) {
        this.orgId = orgId;
        this.assessmentId = assessmentId;
        this.seq = seq;
        this.controlId = controlId;
        this.clause = clause;
        this.requirement = requirement;
        this.result = AssessmentItemResult.PENDING;
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

    /** 由 Service 在评估后回写结论。 */
    void assess(AssessmentItemResult result, String conclusion) {
        this.result = result;
        this.conclusion = conclusion;
    }

    public Long getId() { return id; }
    public Long getOrgId() { return orgId; }
    public Long getAssessmentId() { return assessmentId; }
    public Integer getSeq() { return seq; }
    public Long getControlId() { return controlId; }
    public String getClause() { return clause; }
    public String getRequirement() { return requirement; }
    public AssessmentItemResult getResult() { return result; }
    public String getConclusion() { return conclusion; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
