package com.mandao.grc.modules.dashboard;

/**
 * 合规态势汇总（M·合规态势仪表盘的只读聚合 DTO）。
 *
 * 各计数均在【当前可见组织范围内】统计——服务方法 @Transactional 经隔离切面注入 visible_orgs，
 * 各仓储 findAll 已受 RLS 裁剪，故汇总天然按域隔离。
 */
public record DashboardSummary(
        Risk risk,
        Audit audit,
        Regulatory regulatory,
        Policy policy,
        Permission permission) {

    /** 风险域：未关闭发现、被门控发现、KRI 预警/严重数。 */
    public record Risk(long openFindings, long gatedFindings, long kriWarning, long kriCritical) {
    }

    /** 审计域：未关闭审计发现、未验证整改工单数。 */
    public record Audit(long openFindings, long pendingRemediation) {
    }

    /** 监管域：待报送（未报送/复核中）、已报送数。 */
    public record Regulatory(long pendingFilings, long submittedFilings) {
    }

    /** 制度域：已生效、评审中、草稿数。 */
    public record Policy(long effective, long inReview, long draft) {
    }

    /** 权限域：待审批 SoD 豁免数。 */
    public record Permission(long pendingSodExceptions) {
    }
}
