package com.mandao.grc.modules.workbench;

import java.time.LocalDate;

/**
 * 待办项（工作台·我的待办 的统一条目）。
 *
 * 跨模块归并的待处理工作：type 标明来源（REMEDIATION 整改工单 / COMPLIANCE_ITEM 合规计划项 /
 * REG_FILING 待报送），refId 为对应业务 id，dueDate 为期限（可空），status 为业务态。
 *
 * 注：当前为【可见组织范围内的待办】聚合；与具体登录人的绑定（按角色/责任人过滤"我的"）待 Phase D
 * 鉴权与 RBAC 接入后细化。
 */
public record TodoItem(
        String type,
        Long refId,
        String title,
        LocalDate dueDate,
        String status) {
}
