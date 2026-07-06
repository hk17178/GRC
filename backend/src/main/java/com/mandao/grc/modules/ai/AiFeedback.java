package com.mandao.grc.modules.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * AI 回答反馈（AI 深度包 B32），映射 V63 ai_feedback 表。
 *
 * 记录用户对某次 AI 问答的赞/踩与原因，作为检索/生成质量改进的依据。
 * 隔离锚点 org_id；可见/可写由 RLS 按 app.visible_orgs 自动裁剪（V63 已建 USING/WITH CHECK）。
 * 问题/回答仅存前端提交的摘要，不留全文。
 */
@Entity
@Table(name = "ai_feedback")
public class AiFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    @Column
    private String question;

    @Column
    private String answer;

    /** true=赞 / false=踩。 */
    @Column(nullable = false)
    private boolean helpful;

    @Column(length = 512)
    private String reason;

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    protected AiFeedback() {
    }

    public AiFeedback(Long orgId, String question, String answer, boolean helpful, String reason, String createdBy) {
        this.orgId = orgId;
        this.question = question;
        this.answer = answer;
        this.helpful = helpful;
        this.reason = reason;
        this.createdBy = createdBy;
    }

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = OffsetDateTime.now();
        }
    }

    public Long getId() { return id; }
    public Long getOrgId() { return orgId; }
    public String getQuestion() { return question; }
    public String getAnswer() { return answer; }
    public boolean isHelpful() { return helpful; }
    public String getReason() { return reason; }
    public String getCreatedBy() { return createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
