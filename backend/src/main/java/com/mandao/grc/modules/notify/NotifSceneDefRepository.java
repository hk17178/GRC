package com.mandao.grc.modules.notify;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 通知场景库仓储（D1-8 §九，全局字典）。 */
public interface NotifSceneDefRepository extends JpaRepository<NotifSceneDef, Long> {

    List<NotifSceneDef> findByOrderByIdAsc();
}
