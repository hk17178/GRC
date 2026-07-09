package com.mandao.grc.modules.rbac;

import java.util.List;

/**
 * 权限资源目录（代码即单一真源 · 增强③ R6）。
 *
 * 这里是新增菜单/按钮权限的<b>唯一登记处</b>：加一条 {@link Def} 即可，启动时由 {@link RbacBootstrap}
 * 幂等同步到 resource 表（无需写迁移），超管自动覆盖、配置矩阵自动收录。
 * {@code @RequiresPermission} 引用的 code 必须出现在本目录，否则启动校验拦截（避免"漏登记→超管也被 403"）。
 *
 * 约定：MENU 的 code = 前端 navKey；ACTION 的 code = menu.action，parentMenu 指向其菜单。
 */
public final class ResourceCatalog {

    private ResourceCatalog() {
    }

    /** 资源定义。 */
    public record Def(String code, String name, String type, String parentMenu, int sort) {
    }

    public static final List<Def> ALL = List.of(
            // ---- 菜单 ----
            new Def("dashboard", "合规态势", "MENU", null, 1),
            new Def("todo", "我的待办", "MENU", null, 2),
            new Def("extaudit", "外部审计", "MENU", null, 3),
            new Def("audit", "内部审计", "MENU", null, 4),
            new Def("risk", "风险评估", "MENU", null, 5),
            new Def("law", "法规跟踪", "MENU", null, 6),
            new Def("regaffairs", "监管事项", "MENU", null, 7),
            new Def("obligation", "合规清单", "MENU", null, 8),
            new Def("aml", "反洗钱", "MENU", null, 21), // AML：名单管理+STR报送+义务/自评引用
            new Def("policy", "制度发布", "MENU", null, 9),
            new Def("ai", "AI智能问答", "MENU", null, 10),
            new Def("vendor", "第三方供应商", "MENU", null, 11),
            new Def("org", "组织与资产", "MENU", null, 12),
            new Def("notify", "通知中心", "MENU", null, 13),
            new Def("aimodel", "模型接入", "MENU", null, 14),
            new Def("perm", "权限与审批", "MENU", null, 15),
            new Def("approvalflow", "审批流配置", "MENU", null, 16),
            new Def("board", "看板与留痕", "MENU", null, 17),
            new Def("feedback", "建议与反馈", "MENU", null, 18),
            new Def("settings", "系统设置", "MENU", null, 19),
            new Def("rbacconfig", "权限配置", "MENU", null, 20),
            // ---- 动作（按钮级）----
            new Def("risk.create", "发起评估", "ACTION", "risk", 1),
            new Def("risk.closeFinding", "关闭/验证风险发现", "ACTION", "risk", 2),
            new Def("risk.requestAccept", "申请风险接受", "ACTION", "risk", 3),
            new Def("risk.approveAccept", "审批风险接受", "ACTION", "risk", 4),
            new Def("risk.signoff", "管理层签批", "ACTION", "risk", 5), // 七轮 7-12：签批细粒度门控
            new Def("policy.create", "新建制度", "ACTION", "policy", 1),
            new Def("policy.submit", "提交评审", "ACTION", "policy", 2),
            new Def("policy.decide", "审批/驳回制度", "ACTION", "policy", 3),
            new Def("policy.signoff", "签署/废止制度", "ACTION", "policy", 4),
            new Def("regfiling.create", "登记报送", "ACTION", "regaffairs", 1),
            new Def("regfiling.submit", "提交评审", "ACTION", "regaffairs", 2),
            new Def("regfiling.approve", "审批报送", "ACTION", "regaffairs", 3),
            new Def("vendor.create", "登记供应商", "ACTION", "vendor", 1),
            new Def("vendor.assess", "供应商评估", "ACTION", "vendor", 2),
            new Def("vendor.activate", "启用供应商", "ACTION", "vendor", 3),
            new Def("obligation.create", "登记义务", "ACTION", "obligation", 1),
            new Def("obligation.fulfill", "标记落实", "ACTION", "obligation", 2),
            new Def("org.viewSensitive", "查看敏感数据明文", "ACTION", "org", 1), // B30 数据分级：数据密级凭据（持有=SENSITIVE 密级）

            new Def("approvalflow.save", "保存审批流", "ACTION", "approvalflow", 1),
            new Def("approvalflow.publish", "发布审批流", "ACTION", "approvalflow", 2));

    /** 全部资源 code 集合（启动校验用）。 */
    public static java.util.Set<String> codes() {
        return ALL.stream().map(Def::code).collect(java.util.stream.Collectors.toSet());
    }
}
