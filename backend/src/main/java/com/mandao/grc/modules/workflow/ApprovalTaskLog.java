package com.mandao.grc.modules.workflow;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * 审批决定流水（每次审批/驳回一条，供审计与展示；同时进防篡改哈希链）。携 org_id 隔离锚点。
 */
@Entity
@Table(name = "approval_task_log")
public class ApprovalTaskLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    @Column(name = "instance_id", nullable = false, updatable = false)
    private Long instanceId;

    @Column(name = "node_key", length = 64)
    private String nodeKey;

    @Column(name = "node_name", length = 128)
    private String nodeName;

    @Column(length = 64)
    private String approver;

    @Column(length = 16)
    private String decision;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(name = "decided_at", updatable = false)
    private OffsetDateTime decidedAt;

    protected ApprovalTaskLog() {
    }

    public ApprovalTaskLog(Long orgId, Long instanceId, String nodeKey, String nodeName,
                           String approver, String decision, String comment) {
        this.orgId = orgId;
        this.instanceId = instanceId;
        this.nodeKey = nodeKey;
        this.nodeName = nodeName;
        this.approver = approver;
        this.decision = decision;
        this.comment = comment;
    }

    @PrePersist
    void onCreate() {
        if (this.decidedAt == null) {
            this.decidedAt = OffsetDateTime.now();
        }
    }

    public Long getId() { return id; }
    public Long getOrgId() { return orgId; }
    public Long getInstanceId() { return instanceId; }
    public String getNodeKey() { return nodeKey; }
    public String getNodeName() { return nodeName; }
    public String getApprover() { return approver; }
    public String getDecision() { return decision; }
    public String getComment() { return comment; }
    public OffsetDateTime getDecidedAt() { return decidedAt; }
}
