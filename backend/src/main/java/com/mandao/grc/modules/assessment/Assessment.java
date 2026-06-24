package com.mandao.grc.modules.assessment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 风险评估（业务实体切片）。携带 org_id 隔离锚点；其可见性由 RLS 依据
 * 会话变量 app.visible_orgs 自动裁剪，应用代码无需手写 org 过滤。
 */
@Entity
@Table(name = "assessment")
public class Assessment {

    @Id
    private Long id;

    @Column(name = "org_id", nullable = false)
    private Long orgId;

    private String title;

    @Column(name = "risk_level")
    private String riskLevel;   // VERY_LOW / LOW / MID / HIGH / VERY_HIGH（平台统一五级）

    public Long getId() { return id; }
    public Long getOrgId() { return orgId; }
    public String getTitle() { return title; }
    public String getRiskLevel() { return riskLevel; }
}
