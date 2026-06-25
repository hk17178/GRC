package com.mandao.grc.modules.workflow;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * 审批运行实例（映射一条 Flowable 流程实例）。携 org_id 隔离锚点，RLS 裁剪。
 */
@Entity
@Table(name = "approval_instance")
public class ApprovalInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    @Column(name = "flow_id", nullable = false, updatable = false)
    private Long flowId;

    @Column(name = "flow_version", nullable = false, updatable = false)
    private Integer flowVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "biz_type", nullable = false, length = 32, updatable = false)
    private ApprovalBizType bizType;

    @Column(name = "biz_id", nullable = false, updatable = false)
    private Long bizId;

    @Column(name = "process_instance_id", length = 64)
    private String processInstanceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private InstanceStatus status = InstanceStatus.RUNNING;

    @Column(length = 64)
    private String submitter;

    @Column(name = "started_at", updatable = false)
    private OffsetDateTime startedAt;

    @Column(name = "ended_at")
    private OffsetDateTime endedAt;

    protected ApprovalInstance() {
    }

    public ApprovalInstance(Long orgId, Long flowId, Integer flowVersion, ApprovalBizType bizType,
                            Long bizId, String submitter) {
        this.orgId = orgId;
        this.flowId = flowId;
        this.flowVersion = flowVersion;
        this.bizType = bizType;
        this.bizId = bizId;
        this.submitter = submitter;
        this.status = InstanceStatus.RUNNING;
    }

    @PrePersist
    void onCreate() {
        if (this.startedAt == null) {
            this.startedAt = OffsetDateTime.now();
        }
    }

    void bindProcess(String processInstanceId) {
        this.processInstanceId = processInstanceId;
    }

    /** 流程结束时回写最终状态。 */
    void end(InstanceStatus finalStatus) {
        this.status = finalStatus;
        this.endedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public Long getOrgId() { return orgId; }
    public Long getFlowId() { return flowId; }
    public Integer getFlowVersion() { return flowVersion; }
    public ApprovalBizType getBizType() { return bizType; }
    public Long getBizId() { return bizId; }
    public String getProcessInstanceId() { return processInstanceId; }
    public InstanceStatus getStatus() { return status; }
    public String getSubmitter() { return submitter; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public OffsetDateTime getEndedAt() { return endedAt; }
}
