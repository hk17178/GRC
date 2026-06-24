package com.mandao.grc.modules.obligation;

import com.mandao.grc.modules.audit.HashChainService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 合规清单业务服务（合规义务库 + 落实追踪）。
 *
 * 隔离/留痕范式同其它模块：方法 @Transactional 且位于 com.mandao.grc.modules 包，切面注入 visible_orgs，
 * RLS 裁剪 + 写校验；关键操作经 {@link HashChainService#append} 入按 org 分链的哈希链。
 *
 * 状态机 PENDING → IN_PROGRESS → FULFILLED；可标记 NON_COMPLIANT 后整改回 IN_PROGRESS。
 * ===== 落实闭环（红线）=====
 * 标记已落实 {@link #fulfill} 须提供证据（evidence 非空），否则禁止——义务落实须留证据。
 *
 * 设计依据：需求文档 M·合规清单（义务库/落实）、D2-5。
 */
@Service
public class ObligationService {

    private final ObligationRepository repository;
    private final HashChainService hashChainService;

    public ObligationService(ObligationRepository repository, HashChainService hashChainService) {
        this.repository = repository;
        this.hashChainService = hashChainService;
    }

    @Transactional(readOnly = true)
    public List<Obligation> list() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public Obligation get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("合规义务不存在或不可见：id=" + id));
    }

    /** 登记一条合规义务（PENDING）。 */
    @Transactional
    public Obligation create(Long orgId, String code, String title, String sourceRef, String category,
                             String requirement, String ownerDept, LocalDate dueDate, String actor) {
        Obligation saved = repository.save(
                new Obligation(orgId, code, title, sourceRef, category, requirement, ownerDept, dueDate));
        hashChainService.append(orgId, "OBLIGATION_CREATE", actor, "OBLIGATION:" + saved.getId(),
                "登记合规义务 code=" + code + " 来源=" + sourceRef);
        return saved;
    }

    /** 开始落实：PENDING/NON_COMPLIANT → IN_PROGRESS（NON_COMPLIANT 视为整改启动）。 */
    @Transactional
    public Obligation start(Long id, String actor) {
        Obligation o = get(id);
        if (o.getStatus() != ObligationStatus.PENDING && o.getStatus() != ObligationStatus.NON_COMPLIANT) {
            throw new IllegalStateException("仅待落实/不合规义务可开始落实，当前状态：" + o.getStatus());
        }
        o.setStatus(ObligationStatus.IN_PROGRESS);
        Obligation saved = repository.save(o);
        hashChainService.append(o.getOrgId(), "OBLIGATION_START", actor, "OBLIGATION:" + id, "开始落实");
        return saved;
    }

    /**
     * 标记已落实：IN_PROGRESS → FULFILLED。
     * 【落实闭环红线】须提供证据（evidence 非空），否则抛 {@link IllegalArgumentException}。
     */
    @Transactional
    public Obligation fulfill(Long id, String evidence, String actor) {
        if (evidence == null || evidence.isBlank()) {
            throw new IllegalArgumentException("标记已落实须提供证据（义务落实须留证据）");
        }
        Obligation o = get(id);
        if (o.getStatus() != ObligationStatus.IN_PROGRESS) {
            throw new IllegalStateException("仅落实中义务可标记已落实，当前状态：" + o.getStatus());
        }
        o.setEvidence(evidence);
        o.setStatus(ObligationStatus.FULFILLED);
        Obligation saved = repository.save(o);
        hashChainService.append(o.getOrgId(), "OBLIGATION_FULFILL", actor, "OBLIGATION:" + id, "已落实（留证据）");
        return saved;
    }

    /** 标记不合规：PENDING/IN_PROGRESS → NON_COMPLIANT（待整改）。 */
    @Transactional
    public Obligation markNonCompliant(Long id, String reason, String actor) {
        Obligation o = get(id);
        if (o.getStatus() == ObligationStatus.FULFILLED || o.getStatus() == ObligationStatus.NON_COMPLIANT) {
            throw new IllegalStateException("已落实/已标记不合规的义务不可再标记不合规，当前状态：" + o.getStatus());
        }
        o.setStatus(ObligationStatus.NON_COMPLIANT);
        Obligation saved = repository.save(o);
        hashChainService.append(o.getOrgId(), "OBLIGATION_NONCOMPLIANT", actor, "OBLIGATION:" + id,
                "标记不合规：" + (reason == null ? "" : reason));
        return saved;
    }
}
