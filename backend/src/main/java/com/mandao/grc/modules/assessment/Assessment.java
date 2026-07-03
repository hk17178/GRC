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
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * 风险评估（M2 风险评估业务实体；扩展自 V1 的最小切片）。
 *
 * 携带 org_id 隔离锚点；其可见性与可写性由 RLS 依据会话变量 app.visible_orgs 自动裁剪，
 * 应用代码无需手写 org 过滤（隔离由 {@link com.mandao.grc.common.isolation.OrgScopeAspect} 切面 + RLS 兜底）。
 *
 * 主键来源：V5 为 assessment.id 配独立序列 assessment_id_seq（起始 1000，避开 V1 手工种子 101/102/...），
 * 故用 {@link GenerationType#SEQUENCE} 由 Hibernate 取号；既兼容历史手工赋值行，又支持 Service 新建。
 *
 * 评估状态机：DRAFT → IN_PROGRESS → PENDING_REVIEW → COMPLETED（PENDING_REVIEW 可退回 IN_PROGRESS）。
 */
@Entity
@Table(name = "assessment")
public class Assessment {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "assessment_seq")
    @SequenceGenerator(name = "assessment_seq", sequenceName = "assessment_id_seq", allocationSize = 1)
    private Long id;

    /** 隔离锚点：所属组织。新建时由 Service 依据当前可见上下文设置。 */
    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    @Column(length = 256)
    private String title;

    /** 评估整体风险等级（五级 VERY_LOW/LOW/MID/HIGH/VERY_HIGH）。以枚举名持久化，DB 侧强约束取值。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", length = 16)
    private RiskLevel riskLevel;

    /** 状态机当前态，以字符串持久化（与 DB 的 VARCHAR 一致，便于排查）。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private AssessmentStatus status = AssessmentStatus.DRAFT;

    /** 评估人。 */
    @Column(length = 64)
    private String assessor;

    /** 评估周期（如 2026Q2）。 */
    @Column(length = 16)
    private String period;

    /** 来源评估模板（可空）。表单引擎据此找模板的 ACTIVE 表单渲染填写界面。 */
    @Column(name = "template_id")
    private Long templateId;

    /** 管理层签批：签批人。 */
    @Column(name = "mgmt_signer", length = 64)
    private String mgmtSigner;

    /** 管理层签批：时间。 */
    @Column(name = "mgmt_signed_at")
    private OffsetDateTime mgmtSignedAt;

    /** 管理层签批：意见。 */
    @Column(name = "mgmt_opinion", columnDefinition = "TEXT")
    private String mgmtOpinion;

    /** 管理层签批：是否接受残余风险（CR-002 完成门控放行依据）。 */
    @Column(name = "mgmt_accepted", nullable = false)
    private boolean mgmtAccepted = false;

    /** 手写签名 PNG（V55 签批存证；不随 JSON 外发，查看走专用端点）。 */
    @com.fasterxml.jackson.annotation.JsonIgnore
    @Column(name = "mgmt_signature")
    private byte[] mgmtSignature;

    /** 签名指纹 sha256（随签批写入哈希链，事后可验签名图未被篡改）。 */
    @Column(name = "mgmt_signature_sha256", length = 64)
    private String mgmtSignatureSha256;

    // ===== 背景建立（V46 · ISO 27005/GB/T 20984 ①阶段元数据）=====

    /** 评估范围与边界（系统/业务/部门/场所）。 */
    @Column(columnDefinition = "TEXT")
    private String scope;

    /** 评估目的与背景。 */
    @Column(columnDefinition = "TEXT")
    private String objective;

    /** 依据标准（逗号多值，如 ISO27001,GBT20984,MLPS）。 */
    @Column(length = 256)
    private String basis;

    /** 方式方法（逗号多值：INTERVIEW/DOC_REVIEW/TOOL_SCAN/PENTEST/CONFIG_CHECK）。 */
    @Column(length = 128)
    private String methods;

    /** 评估准则（可能性/影响分级与接受准则说明）。 */
    @Column(columnDefinition = "TEXT")
    private String criteria;

    /** 评估组成员。 */
    @Column(length = 256)
    private String team;

    /** 评估开始日。 */
    @Column(name = "start_date")
    private java.time.LocalDate startDate;

    /** 评估结束日。 */
    @Column(name = "end_date")
    private java.time.LocalDate endDate;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    /** JPA 要求的无参构造。 */
    protected Assessment() {
    }

    /** 业务构造：以草稿态新建一项评估。时间戳由 {@link #onCreate()} 在落库前补齐。 */
    public Assessment(Long orgId, String title, String assessor, String period) {
        this(orgId, title, assessor, period, null);
    }

    /** 业务构造（带来源模板）：以草稿态新建一项评估。 */
    public Assessment(Long orgId, String title, String assessor, String period, Long templateId) {
        this.orgId = orgId;
        this.title = title;
        this.assessor = assessor;
        this.period = period;
        this.templateId = templateId;
        this.status = AssessmentStatus.DRAFT;
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
    public String getTitle() { return title; }
    public RiskLevel getRiskLevel() { return riskLevel; }
    public AssessmentStatus getStatus() { return status; }
    public String getAssessor() { return assessor; }
    public String getPeriod() { return period; }
    public Long getTemplateId() { return templateId; }
    public String getScope() { return scope; }
    public String getObjective() { return objective; }
    public String getBasis() { return basis; }
    public String getMethods() { return methods; }
    public String getCriteria() { return criteria; }
    public String getTeam() { return team; }
    public java.time.LocalDate getStartDate() { return startDate; }
    public java.time.LocalDate getEndDate() { return endDate; }

    /** 背景建立：由 Service 校验后一次性写入评估元数据（V46）。 */
    public void applyContext(String scope, String objective, String basis, String methods,
                             String criteria, String team, java.time.LocalDate startDate, java.time.LocalDate endDate) {
        this.scope = scope;
        this.objective = objective;
        this.basis = basis;
        this.methods = methods;
        this.criteria = criteria;
        this.team = team;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public String getMgmtSigner() { return mgmtSigner; }
    public OffsetDateTime getMgmtSignedAt() { return mgmtSignedAt; }
    public String getMgmtOpinion() { return mgmtOpinion; }
    public boolean isMgmtAccepted() { return mgmtAccepted; }

    /** 由打分服务在保存填写后写入整体残余等级（驱动看板/任务列表风险等级 + 完成门控）。 */
    public void setRiskLevel(RiskLevel riskLevel) {
        this.riskLevel = riskLevel;
    }

    /** 记录一次管理层签批。 */
    public void signOff(String signer, String opinion, boolean accepted) {
        this.mgmtSigner = signer;
        this.mgmtOpinion = opinion;
        this.mgmtAccepted = accepted;
        this.mgmtSignedAt = OffsetDateTime.now();
    }

    /** 附加签批存证（V55：手写签名 PNG + 指纹）。 */
    public void attachSignature(byte[] signature, String sha256) {
        this.mgmtSignature = signature;
        this.mgmtSignatureSha256 = sha256;
    }

    public byte[] getMgmtSignature() { return mgmtSignature; }
    public String getMgmtSignatureSha256() { return mgmtSignatureSha256; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    /** 由 Service 在校验合法流转后调用，推进状态机。 */
    void setStatus(AssessmentStatus status) {
        this.status = status;
    }
}
