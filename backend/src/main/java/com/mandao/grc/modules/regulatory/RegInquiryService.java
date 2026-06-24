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
 * 状态机：OPEN → RESPONDING → CLOSED；非法流转抛 {@link IllegalStateException}。
 *
 * 设计依据：需求文档 M11 监管事项（监管问询）、D1-2 §23、D2-5。
 */
@Service
public class RegInquiryService {

    private final RegInquiryRepository repository;
    private final HashChainService hashChainService;

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

    /** 新建监管问询（OPEN 态）。 */
    @Transactional
    public RegInquiry create(Long orgId, String title, String regulator,
                             LocalDate receivedDate, LocalDate dueDate, String actor) {
        RegInquiry q = new RegInquiry(orgId, title, regulator, receivedDate, dueDate);
        RegInquiry saved = repository.save(q);
        appendLog(saved, "REG_INQUIRY_CREATE", actor,
                "新建监管问询 title=" + title + " regulator=" + regulator + " due=" + dueDate);
        return saved;
    }

    /** 开始答复：OPEN → RESPONDING。 */
    @Transactional
    public RegInquiry respond(Long id, String actor) {
        RegInquiry q = get(id);
        transition(q, RegInquiryStatus.OPEN, RegInquiryStatus.RESPONDING);
        RegInquiry saved = repository.save(q);
        appendLog(saved, "REG_INQUIRY_RESPOND", actor, "开始答复监管问询");
        return saved;
    }

    /** 了结：RESPONDING → CLOSED（终态）。 */
    @Transactional
    public RegInquiry close(Long id, String actor) {
        RegInquiry q = get(id);
        transition(q, RegInquiryStatus.RESPONDING, RegInquiryStatus.CLOSED);
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
