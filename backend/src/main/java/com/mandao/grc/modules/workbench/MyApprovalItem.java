package com.mandao.grc.modules.workbench;

/**
 * 我的审批待办（按登录人角色匹配的待处理审批任务）。
 *
 * 来源：可配置审批流(ApprovalEngine)产生的 Flowable 用户任务，其候选组 = 角色码；
 * 当前登录人持有该角色时，该任务即"分给我"。taskId 用于处置，bizType/bizId 定位业务对象。
 */
public record MyApprovalItem(
        String taskId,
        String bizType,
        Long bizId,
        String nodeName,
        String roleGroup,
        long createdMs) {
}
