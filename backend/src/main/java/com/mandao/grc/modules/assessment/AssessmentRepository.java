package com.mandao.grc.modules.assessment;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 注意：此处没有任何 org 过滤条件。findAll() 看似"查全表"，
 * 但在已注入 app.visible_orgs 的连接上，RLS 会自动只返回可见组织的行——
 * 这正是"隔离不依赖应用代码记得加 where"的兜底价值。
 */
public interface AssessmentRepository extends JpaRepository<Assessment, Long> {

    /** 是否存在引用某模板的评估（模板删除口径：被引用即不可物理删除）。 */
    boolean existsByTemplateId(Long templateId);

    /** 同模板最近一次定稿评估（M2 深度包 B45：复评时复制上一轮背景/范围）。 */
    java.util.Optional<Assessment> findFirstByTemplateIdAndStatusOrderByIdDesc(Long templateId, AssessmentStatus status);
}
