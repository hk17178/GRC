package com.mandao.grc.modules.feedback;

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
 * 建议与反馈（CR-004 反馈管理）。
 *
 * 携带 org_id 隔离锚点；可见性/可写性由 RLS 依据 app.visible_orgs 自动裁剪。
 * 状态机见 {@link FeedbackStatus}；办结须填处置结果（resolution）。
 */
@Entity
@Table(name = "feedback")
public class Feedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 隔离锚点：所属组织。 */
    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    /** 反馈类型。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FeedbackType type;

    /** 标题。 */
    @Column(nullable = false, length = 256)
    private String title;

    /** 正文。 */
    @Column(columnDefinition = "TEXT")
    private String content;

    /** 提交人。 */
    @Column(length = 64)
    private String submitter;

    /** 状态机当前态。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FeedbackStatus status = FeedbackStatus.SUBMITTED;

    /** 处理人（受理时分派）。 */
    @Column(length = 64)
    private String handler;

    /** 处置结果（办结时必填）。 */
    @Column(columnDefinition = "TEXT")
    private String resolution;

    /** 对外回复稿（V43 出站审批：经审批后方可对外发送）。 */
    @Column(name = "outbound_reply", columnDefinition = "TEXT")
    private String outboundReply;

    /** 出站审批状态：NULL 未发起 / PENDING_APPROVAL / APPROVED / REJECTED。 */
    @Column(name = "outbound_status", length = 20)
    private String outboundStatus;

    /** 出站内容 sha256（B29：审批通过时固化，对外发出内容的防篡改指纹）。 */
    @Column(name = "outbound_sha256", length = 64)
    private String outboundSha256;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    /** JPA 要求的无参构造。 */
    protected Feedback() {
    }

    /** 业务构造：以 SUBMITTED 态登记一条反馈。 */
    public Feedback(Long orgId, FeedbackType type, String title, String content, String submitter) {
        this.orgId = orgId;
        this.type = type;
        this.title = title;
        this.content = content;
        this.submitter = submitter;
        this.status = FeedbackStatus.SUBMITTED;
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

    /** 由 Service 在校验后推进状态机。 */
    void setStatus(FeedbackStatus status) {
        this.status = status;
    }

    /** 由 Service 在受理时分派处理人。 */
    void setHandler(String handler) {
        this.handler = handler;
    }

    /** 由 Service 在办结时回写处置结果。 */
    void setResolution(String resolution) {
        this.resolution = resolution;
    }

    /** 由 Service 在出站审批链路上回写回复稿与出站状态。 */
    void setOutbound(String reply, String status) {
        if (reply != null) {
            this.outboundReply = reply;
        }
        this.outboundStatus = status;
    }

    /** B29：出站内容哈希（审批通过时固化，事后校验对外发出内容未被篡改）。 */
    void setOutboundSha256(String sha256) {
        this.outboundSha256 = sha256;
    }

    public String getOutboundSha256() { return outboundSha256; }

    public Long getId() { return id; }
    public Long getOrgId() { return orgId; }
    public FeedbackType getType() { return type; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public String getSubmitter() { return submitter; }
    public FeedbackStatus getStatus() { return status; }
    public String getHandler() { return handler; }
    public String getResolution() { return resolution; }
    public String getOutboundReply() { return outboundReply; }
    public String getOutboundStatus() { return outboundStatus; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
