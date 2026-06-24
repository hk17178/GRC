package com.mandao.grc.modules.policy;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 制度仓储。
 *
 * 注意：此处不写任何 org 过滤条件。findAll() 看似"查全表"，
 * 但在已注入 app.visible_orgs 的连接上，RLS 自动只返回可见组织的行——
 * 隔离不依赖应用代码每次记得加 where（与 AssessmentRepository 同范式）。
 */
public interface PolicyRepository extends JpaRepository<Policy, Long> {
}
