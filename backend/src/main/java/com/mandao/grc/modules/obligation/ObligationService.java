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

    /** 举证链仓储（八轮 8-3，setter 注入）。 */
    private ObligationLinkRepository linkRepository;

    @org.springframework.beans.factory.annotation.Autowired
    void wireLinkRepository(ObligationLinkRepository linkRepository) {
        this.linkRepository = linkRepository;
    }

    @Transactional(readOnly = true)
    public List<Obligation> list() {
        return repository.findAll();
    }

    // ===== 八轮 8-3（B8）：举证链 + 派生满足状态 =====

    /** 列表行：义务 + 举证链摘要 + 派生状态（derivedStatus 只读，人工 status 保留为登记口径）。 */
    public record ObligationRow(Obligation obligation, long linkCount, long evidenceCount,
                                String derivedStatus) {
    }

    /**
     * 带派生状态的义务列表。派生规则（举证链口径，人工填报不再决定满足结论）：
     *  - MET：挂有 制度/控制/评估/审计 任一依据 且 挂有证据（EVIDENCE 链或 fulfill 留证）；
     *  - PARTIAL：有依据但无证据，或有证据但无依据；
     *  - GAP：链上空空如也。
     */
    @Transactional(readOnly = true)
    public List<ObligationRow> listWithDerived() {
        List<Obligation> all = repository.findAll();
        if (all.isEmpty()) {
            return List.of();
        }
        var links = linkRepository.findByObligationIdIn(all.stream().map(Obligation::getId).toList());
        var byOb = new java.util.HashMap<Long, List<ObligationLink>>();
        for (ObligationLink l : links) {
            byOb.computeIfAbsent(l.getObligationId(), k -> new java.util.ArrayList<>()).add(l);
        }
        return all.stream().map(o -> {
            List<ObligationLink> ls = byOb.getOrDefault(o.getId(), List.of());
            long evid = ls.stream().filter(l -> "EVIDENCE".equals(l.getRefType())).count()
                    + (o.getEvidence() == null || o.getEvidence().isBlank() ? 0 : 1);
            long basis = ls.stream().filter(l -> !"EVIDENCE".equals(l.getRefType())).count();
            String derived = (basis > 0 && evid > 0) ? "MET" : (basis + evid > 0 ? "PARTIAL" : "GAP");
            return new ObligationRow(o, ls.size(), evid, derived);
        }).toList();
    }

    /** 义务的举证链明细（依据弹层用）。 */
    @Transactional(readOnly = true)
    public List<ObligationLink> links(Long obligationId) {
        get(obligationId); // 可见性校验
        return linkRepository.findByObligationIdOrderByIdAsc(obligationId);
    }

    /** 挂接依据对象（同对象防重）。 */
    @Transactional
    public ObligationLink addLink(Long obligationId, String refType, Long refId, String note, String actor) {
        Obligation o = get(obligationId);
        if (linkRepository.existsByObligationIdAndRefTypeAndRefId(obligationId, refType, refId)) {
            throw new IllegalStateException("该依据对象已挂接过此义务");
        }
        ObligationLink saved = linkRepository.save(
                new ObligationLink(o.getOrgId(), obligationId, refType, refId, note));
        hashChainService.append(o.getOrgId(), "OBLIGATION_LINK_ADD", actor, "OBLIGATION:" + obligationId,
                "挂接举证依据 " + refType + ":" + refId + (note == null ? "" : "（" + note + "）"));
        return saved;
    }

    /** 摘除依据对象。 */
    @Transactional
    public void removeLink(Long linkId, String actor) {
        ObligationLink l = linkRepository.findById(linkId)
                .orElseThrow(() -> new IllegalArgumentException("举证关联不存在或不可见：id=" + linkId));
        linkRepository.delete(l);
        hashChainService.append(l.getOrgId(), "OBLIGATION_LINK_REMOVE", actor,
                "OBLIGATION:" + l.getObligationId(), "摘除举证依据 " + l.getRefType() + ":" + l.getRefId());
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
