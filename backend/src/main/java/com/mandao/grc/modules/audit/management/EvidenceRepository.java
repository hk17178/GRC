package com.mandao.grc.modules.audit.management;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 证据仓储（RLS 按 visible_orgs 裁剪）。 */
public interface EvidenceRepository extends JpaRepository<Evidence, Long> {

    List<Evidence> findAllByOrderByIdDesc();

    List<Evidence> findByPlanIdOrderByIdDesc(Long planId);

    List<Evidence> findByFindingIdOrderByIdDesc(Long findingId);

    List<Evidence> findByRemediationIdOrderByIdDesc(Long remediationId);

    /** 报送回执证据（七轮 7-2）。 */
    List<Evidence> findByFilingIdOrderByIdDesc(Long filingId);

    /** 重大事件报送/确认材料（七轮 7-2）。 */
    List<Evidence> findByIncidentIdOrderByIdDesc(Long incidentId);

    // ---------- 七轮 7-8：计数门控与列表投影（都不触 bytea） ----------

    /** 整改证据计数（提交门控用——只需知道有没有，不必加载字节）。 */
    long countByRemediationId(Long remediationId);

    /** 报送回执证据计数（了结门控用）。 */
    long countByFilingId(Long filingId);

    /** 重大事件回执证据计数（了结门控用）。 */
    long countByIncidentId(Long incidentId);

    /** 列表投影：可选五维过滤 + 分页，字节列永不出库。 */
    @org.springframework.data.jpa.repository.Query(
            "select e.id as id, e.orgId as orgId, e.planId as planId, e.findingId as findingId, "
                    + "e.remediationId as remediationId, e.filingId as filingId, e.incidentId as incidentId, "
                    + "e.name as name, e.fileName as fileName, e.contentType as contentType, "
                    + "e.sha256 as sha256, e.uploadedBy as uploadedBy, e.uploadedAt as uploadedAt "
                    + "from Evidence e "
                    + "where (:planId is null or e.planId = :planId) "
                    + "and (:findingId is null or e.findingId = :findingId) "
                    + "and (:remediationId is null or e.remediationId = :remediationId) "
                    + "and (:filingId is null or e.filingId = :filingId) "
                    + "and (:incidentId is null or e.incidentId = :incidentId) "
                    + "order by e.id desc")
    List<EvidenceSummary> findSummaries(@org.springframework.data.repository.query.Param("planId") Long planId,
                                        @org.springframework.data.repository.query.Param("findingId") Long findingId,
                                        @org.springframework.data.repository.query.Param("remediationId") Long remediationId,
                                        @org.springframework.data.repository.query.Param("filingId") Long filingId,
                                        @org.springframework.data.repository.query.Param("incidentId") Long incidentId,
                                        org.springframework.data.domain.Pageable pageable);
}
