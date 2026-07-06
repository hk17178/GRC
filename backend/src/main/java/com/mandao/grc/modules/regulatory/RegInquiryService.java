package com.mandao.grc.modules.regulatory;

import com.mandao.grc.modules.audit.HashChainService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 监管问询业务服务（M11 监管事项）。
 *
 * 隔离/留痕范式同 {@link RegFilingService}：@Transactional → 切面注入 visible_orgs，RLS 裁剪 + WITH CHECK；
 * 每次流转 {@link HashChainService#append} 留痕（entity="REG_INQUIRY:{id}"）。
 *
 * 状态机：DRAFTING → REPLIED → AWAIT_FEEDBACK → CLOSED；非法流转抛 {@link IllegalStateException}。
 *
 * 设计依据：需求文档 M11 监管事项（监管问询）、D1-2 §23、D2-5、DM-5 状态机基线。
 */
@Service
public class RegInquiryService {

    private final RegInquiryRepository repository;
    private final HashChainService hashChainService;

    /** 证据仓库（M11 B13：答复须核验回函证据，setter 注入跨模块仓储）。 */
    private com.mandao.grc.modules.audit.management.EvidenceRepository evidenceRepository;

    @org.springframework.beans.factory.annotation.Autowired
    void wireEvidenceRepository(com.mandao.grc.modules.audit.management.EvidenceRepository evidenceRepository) {
        this.evidenceRepository = evidenceRepository;
    }

    public RegInquiryService(RegInquiryRepository repository, HashChainService hashChainService) {
        this.repository = repository;
        this.hashChainService = hashChainService;
    }

    @Transactional(readOnly = true)
    public List<RegInquiry> list() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public RegInquiry get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("监管问询不存在或不可见：id=" + id));
    }

    /** 新建监管问询（DRAFTING 态）。 */
    @Transactional
    public RegInquiry create(Long orgId, String title, String regulator,
                             LocalDate receivedDate, LocalDate dueDate, String actor) {
        RegInquiry q = new RegInquiry(orgId, title, regulator, receivedDate, dueDate);
        RegInquiry saved = repository.save(q);
        appendLog(saved, "REG_INQUIRY_CREATE", actor,
                "新建监管问询 title=" + title + " regulator=" + regulator + " due=" + dueDate);
        return saved;
    }

    /**
     * 答复：DRAFTING → REPLIED。
     * M11 B13（举证门控）：答复前须在证据库挂有本问询的答复材料（给监管的回函/说明附件，
     * sha256 固化）——"口头已回复"而无留痕，是监管举证链的断点。
     */
    @Transactional
    public RegInquiry reply(Long id, String actor) {
        RegInquiry q = get(id);
        if (evidenceRepository.countByInquiryId(id) == 0) {
            throw new IllegalStateException("答复监管问询前须上传答复材料证据（证据库关联本问询，回函/说明原件 sha256 固化留档）");
        }
        transition(q, RegInquiryStatus.DRAFTING, RegInquiryStatus.REPLIED);
        RegInquiry saved = repository.save(q);
        appendLog(saved, "REG_INQUIRY_REPLY", actor, "已答复监管问询");
        return saved;
    }

    /** 转待反馈：REPLIED → AWAIT_FEEDBACK。 */
    @Transactional
    public RegInquiry awaitFeedback(Long id, String actor) {
        RegInquiry q = get(id);
        transition(q, RegInquiryStatus.REPLIED, RegInquiryStatus.AWAIT_FEEDBACK);
        RegInquiry saved = repository.save(q);
        appendLog(saved, "REG_INQUIRY_AWAIT_FEEDBACK", actor, "等待监管反馈");
        return saved;
    }

    /** 了结：AWAIT_FEEDBACK → CLOSED（终态）。 */
    @Transactional
    public RegInquiry close(Long id, String actor) {
        RegInquiry q = get(id);
        transition(q, RegInquiryStatus.AWAIT_FEEDBACK, RegInquiryStatus.CLOSED);
        RegInquiry saved = repository.save(q);
        appendLog(saved, "REG_INQUIRY_CLOSE", actor, "了结监管问询");
        return saved;
    }

    private void transition(RegInquiry q, RegInquiryStatus expectedFrom, RegInquiryStatus to) {
        if (q.getStatus() != expectedFrom) {
            throw new IllegalStateException(
                    "非法状态流转：监管问询 id=" + q.getId()
                            + " 当前状态=" + q.getStatus()
                            + "，仅允许从 " + expectedFrom + " 流转到 " + to);
        }
        q.setStatus(to);
    }

    private void appendLog(RegInquiry q, String action, String actor, String detail) {
        hashChainService.append(q.getOrgId(), action, actor, "REG_INQUIRY:" + q.getId(), detail);
    }
}
