package com.mandao.grc.modules.workflow;

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
 * 流程绑定（D1-8 §八 自定义工作流 H-06），映射 V74 process_binding 表。
 *
 * 按 (object_type + org_id + condition) 绑定一个 Flowable 流程定义（process_def_key + version）。
 * condition 为声明式 JSON（谓词 AND，空=兜底）；运行期由 {@link ProcessBindingService} 按单据上下文匹配选流程。
 * 版本快照固化：单据发起时把选中 key+version 记到单据上，后续改绑定不影响在途单据。
 * 隔离锚点 org_id；可见/可写由 RLS 按 app.visible_orgs 自动裁剪。
 */
@Entity
@Table(name = "process_binding")
public class ProcessBinding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    @Column(name = "object_type", nullable = false, length = 32)
    private String objectType;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String condition;

    @Column(name = "process_def_key", nullable = false, length = 64)
    private String processDefKey;

    @Column(name = "process_version", nullable = false)
    private int processVersion;

    @Column(nullable = false)
    private int seq;

    @Column(nullable = false, length = 16)
    private String status = "ACTIVE";

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    protected ProcessBinding() {
    }

    public ProcessBinding(Long orgId, String objectType, String name, String condition,
                          String processDefKey, int processVersion, int seq, String createdBy) {
        this.orgId = orgId;
        this.objectType = objectType;
        this.name = name;
        this.condition = condition;
        this.processDefKey = processDefKey;
        this.processVersion = processVersion;
        this.seq = seq;
        this.createdBy = createdBy;
        this.status = "ACTIVE";
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

    public Long getId() { return id; }
    public Long getOrgId() { return orgId; }
    public String getObjectType() { return objectType; }
    public String getName() { return name; }
    public String getCondition() { return condition; }
    public String getProcessDefKey() { return processDefKey; }
    public int getProcessVersion() { return processVersion; }
    public int getSeq() { return seq; }
    public String getStatus() { return status; }
    public String getCreatedBy() { return createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    void setStatus(String status) { this.status = status; }
}
