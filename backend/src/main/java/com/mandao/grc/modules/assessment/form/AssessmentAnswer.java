package com.mandao.grc.modules.assessment.form;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * 评估填写结果：一个评估对应一份填写（assessment_id 唯一）。
 *
 * 启动填写时绑定当时的 ACTIVE 表单版本（form_version_id=快照），此后模板修订不影响本次填写。
 * answers_json 以字段 key 存标量值、以列表 key 存明细行数组（JSON 文本，P1 用 TEXT）。
 * 携带 org_id 隔离锚点，RLS 按 visible_orgs 裁剪。
 */
@Entity
@Table(name = "assessment_answer")
public class AssessmentAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    @Column(name = "assessment_id", nullable = false, updatable = false)
    private Long assessmentId;

    @Column(name = "form_version_id", nullable = false)
    private Long formVersionId;

    /** 填写值 JSON 文本（按字段/列表 key）。 */
    @Column(name = "answers_json", nullable = false, columnDefinition = "TEXT")
    private String answersJson = "{}";

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    protected AssessmentAnswer() {
    }

    public AssessmentAnswer(Long orgId, Long assessmentId, Long formVersionId, String answersJson) {
        this.orgId = orgId;
        this.assessmentId = assessmentId;
        this.formVersionId = formVersionId;
        this.answersJson = answersJson == null ? "{}" : answersJson;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        this.updatedAt = OffsetDateTime.now();
    }

    public void setAnswersJson(String answersJson) {
        this.answersJson = answersJson == null ? "{}" : answersJson;
    }

    public Long getId() { return id; }
    public Long getOrgId() { return orgId; }
    public Long getAssessmentId() { return assessmentId; }
    public Long getFormVersionId() { return formVersionId; }
    public String getAnswersJson() { return answersJson; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
