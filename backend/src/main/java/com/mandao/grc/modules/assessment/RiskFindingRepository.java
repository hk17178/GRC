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

    /** 场景引用检查（八轮 8-10：被风险发现引用的场景不可删）。 */
    boolean existsByScenarioId(Long scenarioId);

    /** 登记册分页护栏（七轮 7-8：跨评估聚合是增长最快的全量查询之一）。 */
    List<RiskFinding> findAllByOrderByIdDesc(org.springframework.data.domain.Pageable pageable);

    /** 同一评估内是否已由该场景生成过发现（V48 防重复生成）。 */
    boolean existsByAssessmentIdAndScenarioId(Long assessmentId, Long scenarioId);
}
