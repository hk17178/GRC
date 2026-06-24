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
 *
 * 设计依据：需求文档 M11 监管事项（重大事件报送）、D1-2 §23、D2-5。
 */
@Service
public class MajorIncidentService {

    private final MajorIncidentRepository repository;
    private final HashChainService hashChainService;

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

    /** 新建重大事件报送（DRAFT 态）。 */
    @Transactional
    public MajorIncidentReport create(Long orgId, String title, String severity,
                                      OffsetDateTime occurredAt, String actor) {
        MajorIncidentReport m = new MajorIncidentReport(orgId, title, severity, occurredAt);
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

    /** 了结：REPORTED → CLOSED（终态）。 */
    @Transactional
    public MajorIncidentReport close(Long id, String actor) {
        MajorIncidentReport m = get(id);
        transition(m, MajorIncidentStatus.REPORTED, MajorIncidentStatus.CLOSED);
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
