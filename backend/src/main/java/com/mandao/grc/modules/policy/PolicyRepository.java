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

    /** 列表投影（七轮 7-8）：不加载 docBytes 原件字节；分页由调用方给 Pageable。 */
    java.util.List<PolicySummary> findAllProjectedByOrderByIdDesc(org.springframework.data.domain.Pageable pageable);

    /** 状态计数（七轮 7-8：仪表盘只要数量，不必把全部制度实体拉进堆）。 */
    long countByStatus(PolicyStatus status);

    /** 轻量三元组（id/标题/状态，AI 匹配建议提示词用，不触 bytea）。 */
    @org.springframework.data.jpa.repository.Query("select p.id, p.title, p.status from Policy p order by p.id")
    java.util.List<Object[]> findIdTitleStatus();
}
