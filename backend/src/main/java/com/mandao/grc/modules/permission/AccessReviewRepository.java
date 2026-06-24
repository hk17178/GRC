package com.mandao.grc.modules.permission;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * UAR 权限审阅主表仓储（org-scoped）。
 *
 * 不写任何 org 过滤条件：在已注入 app.visible_orgs 的连接上，RLS 自动只返回可见组织的行。
 */
public interface AccessReviewRepository extends JpaRepository<AccessReview, Long> {
}
