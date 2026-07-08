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
 * 流程发起快照（D1-8 §八 H-06 接线），映射 V76 process_launch 表。
 *
 * 单据发起审批时把本次选中的 process_def_key + version（+命中的 binding_id）固化于此——
 * 后续改流程绑定不影响在途单据（快照为发起时点的不可变记录）。隔离锚点 org_id + RLS。
 */
@Entity
@Table(name = "process_launch")
public class ProcessLaunch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    @Column(name = "biz_type", nullable = false, length = 48)
    private String bizType;

    @Column(name = "biz_id", nullable = false)
    private Long bizId;

    @Column(name = "process_def_key", nullable = false, length = 64)
    private String processDefKey;

    @Column(name = "process_version", nullable = false)
    private int processVersion;

    @Column(name = "binding_id")
    private Long bindingId;

    @Column(name = "process_instance_id", nullable = false, length = 64)
    private String processInstanceId;

    @Column(length = 64)
    private String submitter;

    @Column(name = "launched_at", updatable = false)
    private OffsetDateTime launchedAt;

    protected ProcessLaunch() {
    }

    public ProcessLaunch(Long orgId, String bizType, Long bizId, String processDefKey, int processVersion,
                         Long bindingId, String processInstanceId, String submitter) {
        this.orgId = orgId;
        this.bizType = bizType;
        this.bizId = bizId;
        this.processDefKey = processDefKey;
        this.processVersion = processVersion;
        this.bindingId = bindingId;
        this.processInstanceId = processInstanceId;
        this.submitter = submitter;
    }

    @PrePersist
    void onCreate() {
        if (this.launchedAt == null) {
            this.launchedAt = OffsetDateTime.now();
        }
    }

    public Long getId() { return id; }
    public Long getOrgId() { return orgId; }
    public String getBizType() { return bizType; }
    public Long getBizId() { return bizId; }
    public String getProcessDefKey() { return processDefKey; }
    public int getProcessVersion() { return processVersion; }
    public Long getBindingId() { return bindingId; }
    public String getProcessInstanceId() { return processInstanceId; }
    public String getSubmitter() { return submitter; }
    public OffsetDateTime getLaunchedAt() { return launchedAt; }
}
