package com.mandao.grc.modules.permission;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * UAR 逐项审阅仓储（org-scoped）。
 *
 * 不写任何 org 过滤条件：在已注入 app.visible_orgs 的连接上，RLS 自动只返回可见组织的行。
 */
public interface AccessReviewItemRepository extends JpaRepository<AccessReviewItem, Long> {

    /** 列出某审阅下的全部审阅项（仍受 RLS 裁剪）。 */
    List<AccessReviewItem> findByAccessReviewId(Long accessReviewId);
}
