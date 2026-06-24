package com.mandao.grc.modules.assessment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 风险接受仓储。
 *
 * 不写任何 org 过滤条件：RLS 在已注入 visible_orgs 的连接上自动裁剪可见行。
 */
public interface RiskAcceptanceRepository extends JpaRepository<RiskAcceptance, Long> {

    /** 列出某风险发现的全部风险接受记录（仍受 RLS 裁剪）。 */
    List<RiskAcceptance> findByFindingId(Long findingId);

    /** 取某风险发现指定状态、最新的一条接受记录（用于审批处置时定位 PENDING 申请）。 */
    Optional<RiskAcceptance> findFirstByFindingIdAndStatusOrderByIdDesc(Long findingId, AcceptanceStatus status);
}
