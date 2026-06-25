package com.mandao.grc.modules.workflow;

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
 * 可配置审批流定义（每组织每业务类型一套）。
 *
 * 携 org_id 隔离锚点；RLS 按 visible_orgs 裁剪。graphJson 为画布源，发布时编译成 BPMN 部署，
 * bpmnKey 记部署后的 Flowable 流程 key。同组织同业务类型仅一条 ACTIVE（DB 部分唯一索引保证）。
 */
@Entity
@Table(name = "approval_flow")
public class ApprovalFlow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    @Enumerated(EnumType.STRING)
    @Column(name = "biz_type", nullable = false, length = 32)
    private ApprovalBizType bizType;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false)
    private Integer version = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FlowStatus status = FlowStatus.DRAFT;

    /** 画布源（节点+连线+属性的 JSON）。 */
    @Column(name = "graph_json", columnDefinition = "TEXT")
    private String graphJson;

    /** 发布后部署的 Flowable 流程 key（未发布为空）。 */
    @Column(name = "bpmn_key", length = 64)
    private String bpmnKey;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    protected ApprovalFlow() {
    }

    /** 业务构造：以 DRAFT 态新建一套流程定义。 */
    public ApprovalFlow(Long orgId, ApprovalBizType bizType, String name, String graphJson) {
        this.orgId = orgId;
        this.bizType = bizType;
        this.name = name;
        this.graphJson = graphJson;
        this.status = FlowStatus.DRAFT;
        this.version = 1;
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

    /** 更新草稿内容（名称 + 画布）。 */
    void updateDraft(String name, String graphJson) {
        this.name = name;
        this.graphJson = graphJson;
    }

    /** 发布：记录编译部署的流程 key，置 ACTIVE，版本+1。 */
    void activate(String bpmnKey) {
        this.bpmnKey = bpmnKey;
        this.status = FlowStatus.ACTIVE;
        this.version = this.version + 1;
    }

    /** 停用。 */
    void retire() {
        this.status = FlowStatus.RETIRED;
    }

    public Long getId() { return id; }
    public Long getOrgId() { return orgId; }
    public ApprovalBizType getBizType() { return bizType; }
    public String getName() { return name; }
    public Integer getVersion() { return version; }
    public FlowStatus getStatus() { return status; }
    public String getGraphJson() { return graphJson; }
    public String getBpmnKey() { return bpmnKey; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
