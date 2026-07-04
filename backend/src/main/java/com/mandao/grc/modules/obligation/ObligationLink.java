package com.mandao.grc.modules.obligation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * 义务举证链关联（八轮 8-3 / 评估报告 B8）：
 * 一条合规义务挂多类依据对象——制度(POLICY)/控制点(CONTROL)/评估(ASSESSMENT)/审计(AUDIT)/证据(EVIDENCE)，
 * 形成「法规条款 → 义务 → 制度/控制 → 评估/审计 → 证据」的完整举证链；
 * 满足状态由链上对象【派生只读】，不再人工直填（M4-6/7/8 + CR-002 口径）。
 */
@Entity
@Table(name = "obligation_link")
public class ObligationLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    @Column(name = "obligation_id", nullable = false)
    private Long obligationId;

    /** 关联对象类型：POLICY / CONTROL / ASSESSMENT / AUDIT / EVIDENCE。 */
    @Column(name = "ref_type", nullable = false, length = 16)
    private String refType;

    @Column(name = "ref_id", nullable = false)
    private Long refId;

    @Column(length = 256)
    private String note;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    protected ObligationLink() {
    }

    public ObligationLink(Long orgId, Long obligationId, String refType, Long refId, String note) {
        this.orgId = orgId;
        this.obligationId = obligationId;
        this.refType = refType;
        this.refId = refId;
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
    public Long getObligationId() { return obligationId; }
    public String getRefType() { return refType; }
    public Long getRefId() { return refId; }
    public String getNote() { return note; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
