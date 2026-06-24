package com.mandao.grc.modules.control;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 控制项-框架条款映射（统一控件库的"一控多框架"载体）。
 *
 * 携带 org_id 隔离锚点（与所属控制项同组织）；可见性/可写性由 RLS 自动裁剪。
 * 一个控制项可有多条映射，分别指向不同框架的具体条款（clause）。
 */
@Entity
@Table(name = "control_framework_ref")
public class ControlFrameworkRef {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 隔离锚点：所属组织。 */
    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    /** 所属控制项。 */
    @Column(name = "control_id", nullable = false, updatable = false)
    private Long controlId;

    /** 映射到的合规框架。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ControlFramework framework;

    /** 框架内条款编号（如 ISO A.9.2.1、等保 8.1.4.2）。 */
    @Column(nullable = false, length = 64)
    private String clause;

    /** JPA 要求的无参构造。 */
    protected ControlFrameworkRef() {
    }

    /** 业务构造：登记一条控制项到框架条款的映射。 */
    public ControlFrameworkRef(Long orgId, Long controlId, ControlFramework framework, String clause) {
        this.orgId = orgId;
        this.controlId = controlId;
        this.framework = framework;
        this.clause = clause;
    }

    public Long getId() { return id; }
    public Long getOrgId() { return orgId; }
    public Long getControlId() { return controlId; }
    public ControlFramework getFramework() { return framework; }
    public String getClause() { return clause; }
}
