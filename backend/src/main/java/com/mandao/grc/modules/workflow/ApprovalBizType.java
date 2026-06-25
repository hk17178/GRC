package com.mandao.grc.modules.workflow;

/**
 * 可配置审批的业务类型（每组织每类型一套审批流）。
 * 现有 4 条审批化流程对应前 4 项；可按需扩展。
 */
public enum ApprovalBizType {
    /** 制度发布（M1）。 */
    POLICY_PUBLISH,
    /** 风险接受（M2 · CR-002）。 */
    RISK_ACCEPT,
    /** SoD 例外（M8）。 */
    SOD_EXCEPTION,
    /** 监管报送（M11）。 */
    REG_FILING
}
