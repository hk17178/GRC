package com.mandao.grc.modules.notify;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 通知升级链仓储（D1-8 §九）。RLS 按 visible_orgs 裁剪。 */
public interface NotificationEscalationRepository extends JpaRepository<NotificationEscalation, Long> {

    List<NotificationEscalation> findBySceneIdAndStatusOrderByLevelAsc(Long sceneId, String status);
}
