package com.mandao.grc.modules.policy;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 制度签署确认仓储。
 *
 * 与其它仓储一致：不手写 org 过滤，隔离由切面 + RLS 兜底保证。
 * 重复签署由表上 UNIQUE(policy_id, signer) 约束在落库时拦截。
 */
public interface PolicySignoffRepository extends JpaRepository<PolicySignoff, Long> {
}
