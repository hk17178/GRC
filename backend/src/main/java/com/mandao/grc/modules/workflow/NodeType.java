package com.mandao.grc.modules.workflow;

/**
 * 审批流画布节点类型。
 *  START/END  ：起止（END 带 outcome=APPROVED/REJECTED）；
 *  APPROVAL   ：审批节点（会签 ALL / 或签 ANY、人数、超时升级）；
 *  CONDITION  ：条件路由（按表达式分支）；
 *  PARALLEL_SPLIT/JOIN：并行分叉 / 合流（全部回来才继续）。
 */
public enum NodeType {
    START,
    APPROVAL,
    CONDITION,
    PARALLEL_SPLIT,
    PARALLEL_JOIN,
    END
}
