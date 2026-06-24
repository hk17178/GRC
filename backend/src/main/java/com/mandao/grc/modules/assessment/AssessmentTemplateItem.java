package com.mandao.grc.modules.assessment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 评估模板项（模板内的一条检查项）。
 *
 * 携带 org_id 隔离锚点（与所属模板同组织）；可见性/可写性由 RLS 自动裁剪。
 * 可引用统一控件库的控制项（control_id，实现"评估-控件复用"的源头），并记框架条款与检查要求。
 */
@Entity
@Table(name = "assessment_template_item")
public class AssessmentTemplateItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 隔离锚点：所属组织。 */
    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    /** 所属模板。 */
    @Column(name = "template_id", nullable = false, updatable = false)
    private Long templateId;

    /** 排序序号。 */
    @Column(nullable = false)
    private Integer seq;

    /** 引用的统一控制项 id（可空：未必每项都映射到控件库）。 */
    @Column(name = "control_id")
    private Long controlId;

    /** 框架条款编号。 */
    @Column(length = 64)
    private String clause;

    /** 检查要求/说明。 */
    @Column(columnDefinition = "TEXT")
    private String requirement;

    /** JPA 要求的无参构造。 */
    protected AssessmentTemplateItem() {
    }

    /** 业务构造：登记一条模板检查项。 */
    public AssessmentTemplateItem(Long orgId, Long templateId, Integer seq, Long controlId,
                                  String clause, String requirement) {
        this.orgId = orgId;
        this.templateId = templateId;
        this.seq = seq;
        this.controlId = controlId;
        this.clause = clause;
        this.requirement = requirement;
    }

    public Long getId() { return id; }
    public Long getOrgId() { return orgId; }
    public Long getTemplateId() { return templateId; }
    public Integer getSeq() { return seq; }
    public Long getControlId() { return controlId; }
    public String getClause() { return clause; }
    public String getRequirement() { return requirement; }
}
