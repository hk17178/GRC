package com.mandao.grc.modules.notify;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 通知配置仓储（查询自动受 RLS 裁剪）。 */
public interface NotifyConfigRepository extends JpaRepository<NotifyConfig, Long> {

    List<NotifyConfig> findByKindOrderByIdAsc(String kind);
}
