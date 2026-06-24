package com.mandao.grc.modules.assessment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 风险发现仓储。
 *
 * 不写任何 org 过滤条件：在已注入 app.visible_orgs 的连接上，RLS 自动只返回可见组织的行
 * （与 PolicyRepository/AssessmentRepository 同范式）。
 */
public interface RiskFindingRepository extends JpaRepository<RiskFinding, Long> {

    /** 列出某评估下的全部风险发现（仍受 RLS 裁剪）。 */
    List<RiskFinding> findByAssessmentId(Long assessmentId);
}
