package com.mandao.grc.modules.notify;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 运行态通知场景仓储（D1-8 §九）。RLS 按 visible_orgs 裁剪。 */
public interface NotificationSceneRepository extends JpaRepository<NotificationScene, Long> {

    List<NotificationScene> findByOrderByIdAsc();

    List<NotificationScene> findByStatusOrderByIdAsc(String status);
}
