package com.mandao.grc.modules.feedback;

import com.mandao.grc.modules.audit.HashChainService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 建议与反馈业务服务（CR-004 反馈管理）。
 *
 * 隔离/留痕范式同其它模块：方法 @Transactional 且位于 com.mandao.grc.modules 包，切面注入 visible_orgs，
 * RLS 裁剪 + 写校验；关键操作经 {@link HashChainService#append} 入按 org 分链的哈希链。
 *
 * 状态机 SUBMITTED → IN_PROGRESS → RESOLVED → CLOSED；SUBMITTED/IN_PROGRESS 可 REJECTED。
 * ===== 办结闭环（红线）=====
 * 办结 {@link #resolve} 须填处置结果（resolution 非空），否则禁止。
 *
 * 设计依据：需求文档 CR-004（反馈建议）、D2-5。
 */
@Service
public class FeedbackService {

    private final FeedbackRepository repository;
    private final HashChainService hashChainService;

    public FeedbackService(FeedbackRepository repository, HashChainService hashChainService) {
        this.repository = repository;
        this.hashChainService = hashChainService;
    }

    @Transactional(readOnly = true)
    public List<Feedback> list() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public Feedback get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("反馈不存在或不可见：id=" + id));
    }

    /** 提交反馈（SUBMITTED）。 */
    @Transactional
    public Feedback submit(Long orgId, FeedbackType type, String title, String content,
                           String submitter, String actor) {
        Feedback saved = repository.save(new Feedback(orgId, type, title, content, submitter));
        hashChainService.append(orgId, "FEEDBACK_SUBMIT", actor, "FEEDBACK:" + saved.getId(),
                "提交反馈 type=" + type + " title=" + title);
        return saved;
    }

    /** 受理：SUBMITTED → IN_PROGRESS，分派处理人。 */
    @Transactional
    public Feedback triage(Long id, String handler, String actor) {
        Feedback f = get(id);
        if (f.getStatus() != FeedbackStatus.SUBMITTED) {
            throw new IllegalStateException("仅已提交(SUBMITTED)反馈可受理，当前状态：" + f.getStatus());
        }
        f.setStatus(FeedbackStatus.IN_PROGRESS);
        f.setHandler(handler);
        Feedback saved = repository.save(f);
        hashChainService.append(f.getOrgId(), "FEEDBACK_TRIAGE", actor, "FEEDBACK:" + id, "受理并分派 handler=" + handler);
        return saved;
    }

    /**
     * 办结：IN_PROGRESS → RESOLVED。
     * 【办结闭环红线】须填处置结果（resolution 非空），否则抛 {@link IllegalArgumentException}。
     */
    @Transactional
    public Feedback resolve(Long id, String resolution, String actor) {
        if (resolution == null || resolution.isBlank()) {
            throw new IllegalArgumentException("办结须填处置结果");
        }
        Feedback f = get(id);
        if (f.getStatus() != FeedbackStatus.IN_PROGRESS) {
            throw new IllegalStateException("仅处理中反馈可办结，当前状态：" + f.getStatus());
        }
        f.setResolution(resolution);
        f.setStatus(FeedbackStatus.RESOLVED);
        Feedback saved = repository.save(f);
        hashChainService.append(f.getOrgId(), "FEEDBACK_RESOLVE", actor, "FEEDBACK:" + id, "办结（含处置结果）");
        return saved;
    }

    /** 关闭：RESOLVED → CLOSED（终态）。 */
    @Transactional
    public Feedback close(Long id, String actor) {
        Feedback f = get(id);
        if (f.getStatus() != FeedbackStatus.RESOLVED) {
            throw new IllegalStateException("仅已办结(RESOLVED)反馈可关闭，当前状态：" + f.getStatus());
        }
        f.setStatus(FeedbackStatus.CLOSED);
        Feedback saved = repository.save(f);
        hashChainService.append(f.getOrgId(), "FEEDBACK_CLOSE", actor, "FEEDBACK:" + id, "关闭反馈");
        return saved;
    }

    /** 驳回：SUBMITTED/IN_PROGRESS → REJECTED（终态）。 */
    @Transactional
    public Feedback reject(Long id, String reason, String actor) {
        Feedback f = get(id);
        if (f.getStatus() != FeedbackStatus.SUBMITTED && f.getStatus() != FeedbackStatus.IN_PROGRESS) {
            throw new IllegalStateException("仅未办结反馈可驳回，当前状态：" + f.getStatus());
        }
        f.setStatus(FeedbackStatus.REJECTED);
        Feedback saved = repository.save(f);
        hashChainService.append(f.getOrgId(), "FEEDBACK_REJECT", actor, "FEEDBACK:" + id,
                "驳回反馈：" + (reason == null ? "" : reason));
        return saved;
    }
}
