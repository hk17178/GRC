package com.mandao.grc.modules.regulatory;

import com.mandao.grc.modules.audit.HashChainService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 重大事件报送业务服务（M11 监管事项）。
 *
 * 隔离/留痕范式同 {@link RegFilingService}：@Transactional → 切面注入 visible_orgs，RLS 裁剪 + WITH CHECK；
 * 每次流转 {@link HashChainService#append} 留痕（entity="MAJOR_INCIDENT:{id}"）。
 *
 * 状态机：DRAFT → REPORTED → CLOSED；非法流转抛 {@link IllegalStateException}。
 * report 流转时记录 reported_at（上报监管时刻）。
 * severity 采用平台五级 {@link MajorIncidentSeverity}（VERY_LOW/LOW/MID/HIGH/VERY_HIGH）。
 *
 * 设计依据：需求文档 M11 监管事项（重大事件报送）、D1-2 §23、D2-5、DM-5 严重度对齐基线。
 */
@Service
public class MajorIncidentService {

    private final MajorIncidentRepository repository;
    private final HashChainService hashChainService;

    /** 证据仓库（七轮 7-2：了结须核验回执证据，setter 注入跨模块仓储）。 */
    private com.mandao.grc.modules.audit.management.EvidenceRepository evidenceRepository;

    @org.springframework.beans.factory.annotation.Autowired
    void wireEvidenceRepository(com.mandao.grc.modules.audit.management.EvidenceRepository evidenceRepository) {
        this.evidenceRepository = evidenceRepository;
    }

    public MajorIncidentService(MajorIncidentRepository repository, HashChainService hashChainService) {
        this.repository = repository;
        this.hashChainService = hashChainService;
    }

    @Transactional(readOnly = true)
    public List<MajorIncidentReport> list() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public MajorIncidentReport get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("重大事件报送不存在或不可见：id=" + id));
    }

    /** 新建重大事件报送（DRAFT 态）。七轮 7-2：可携法定报送时限（到期扫描按 3/1/0 天预警）。 */
    @Transactional
    public MajorIncidentReport create(Long orgId, String title, MajorIncidentSeverity severity,
                                      OffsetDateTime occurredAt, java.time.LocalDate reportDeadline, String actor) {
        MajorIncidentReport m = new MajorIncidentReport(orgId, title, severity, occurredAt);
        m.setReportDeadline(reportDeadline);
        MajorIncidentReport saved = repository.save(m);
        appendLog(saved, "MAJOR_INCIDENT_CREATE", actor,
                "新建重大事件报送 title=" + title + " severity=" + severity + " occurred=" + occurredAt);
        return saved;
    }

    /** 上报监管：DRAFT → REPORTED（记录 reported_at）。 */
    @Transactional
    public MajorIncidentReport report(Long id, String actor) {
        MajorIncidentReport m = get(id);
        transition(m, MajorIncidentStatus.DRAFT, MajorIncidentStatus.REPORTED);
        m.setReportedAt(OffsetDateTime.now());
        MajorIncidentReport saved = repository.save(m);
        appendLog(saved, "MAJOR_INCIDENT_REPORT", actor, "已上报监管机构");
        return saved;
    }

    /** 监管确认收到：REPORTED → ACKNOWLEDGED（七轮 7-2/B3，记录 acknowledged_at）。 */
    @Transactional
    public MajorIncidentReport acknowledge(Long id, String actor) {
        MajorIncidentReport m = get(id);
        transition(m, MajorIncidentStatus.REPORTED, MajorIncidentStatus.ACKNOWLEDGED);
        m.setAcknowledgedAt(OffsetDateTime.now());
        MajorIncidentReport saved = repository.save(m);
        appendLog(saved, "MAJOR_INCIDENT_ACK", actor, "监管机构确认收到报送");
        return saved;
    }

    /**
     * 了结：ACKNOWLEDGED → CLOSED（终态）。
     * 七轮 7-2（B3 红线）：须先经监管确认（ACKNOWLEDGED）且证据库挂有本事件的报送回执/确认材料。
     */
    @Transactional
    public MajorIncidentReport close(Long id, String actor) {
        MajorIncidentReport m = get(id);
        if (evidenceRepository.countByIncidentId(id) == 0) {
            throw new IllegalStateException("重大事件了结前须上传报送回执/监管确认材料（证据库关联本事件，sha256 固化）");
        }
        transition(m, MajorIncidentStatus.ACKNOWLEDGED, MajorIncidentStatus.CLOSED);
        MajorIncidentReport saved = repository.save(m);
        appendLog(saved, "MAJOR_INCIDENT_CLOSE", actor, "了结重大事件报送");
        return saved;
    }

    private void transition(MajorIncidentReport m, MajorIncidentStatus expectedFrom, MajorIncidentStatus to) {
        if (m.getStatus() != expectedFrom) {
            throw new IllegalStateException(
                    "非法状态流转：重大事件报送 id=" + m.getId()
                            + " 当前状态=" + m.getStatus()
                            + "，仅允许从 " + expectedFrom + " 流转到 " + to);
        }
        m.setStatus(to);
    }

    private void appendLog(MajorIncidentReport m, String action, String actor, String detail) {
        hashChainService.append(m.getOrgId(), action, actor, "MAJOR_INCIDENT:" + m.getId(), detail);
    }
}
