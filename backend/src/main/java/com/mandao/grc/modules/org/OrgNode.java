package com.mandao.grc.modules.org;

/**
 * 组织树节点（M6 组织管理）投影。
 *
 * 不是 JPA 实体——org 表是组织字典（V1 建，无 RLS，供 VisibleOrgsService 计算可见域），
 * 本模块仅以只读投影 + 原生 SQL 增改它，避免给 org 引入实体/序列改动而影响既有隔离。
 *
 * 物化路径 path：根 '/1'，子组织 path = 父path + '/' + 新id（与 V1 种子一致）。
 *
 * 设计依据：需求文档 M6 组织与资产、D1-2、D2-5。
 */
public record OrgNode(Long id, Long parentId, String orgType, String code, String name, String path) {
}
