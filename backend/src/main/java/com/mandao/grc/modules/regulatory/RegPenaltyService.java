package com.mandao.grc.modules.regulatory;

import com.mandao.grc.modules.audit.HashChainService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 处罚约谈业务服务（M11 监管事项）。
 *
 * 隔离/留痕范式同 {@link RegFilingService}：@Transactional → 切面注入 visible_orgs，RLS 裁剪 + WITH CHECK；
 * 每次流转 {@link HashChainService#append} 留痕（entity="REG_PENALTY:{id}"）。
 *
 * 状态机：OPEN → RECTIFYING → CLOSED；非法流转抛 {@link IllegalStateException}。
 *
 * 设计依据：需求文档 M11 监管事项（处罚约谈）、D1-2 §23、D2-5。
 */
@Service
public class RegPenaltyService {

    private final RegPenaltyRepository repository;
    private final HashChainService hashChainService;

    /** 证据仓库（M11 B13：了结须核验整改/缴款证据，setter 注入跨模块仓储）。 */
    private com.mandao.grc.modules.audit.management.EvidenceRepository evidenceRepository;

    @org.springframework.beans.factory.annotation.Autowired
    void wireEvidenceRepository(com.mandao.grc.modules.audit.management.EvidenceRepository evidenceRepository) {
        this.evidenceRepository = evidenceRepository;
    }

    public RegPenaltyService(RegPenaltyRepository repository, HashChainService hashChainService) {
        this.repository = repository;
        this.hashChainService = hashChainService;
    }

    @Transactional(readOnly = true)
    public List<RegPenalty> list() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public RegPenalty get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("处罚约谈记录不存在或不可见：id=" + id));
    }

    /** 新建处罚/约谈记录（OPEN 态）。 */
    @Transactional
    public RegPenalty create(Long orgId, String title, String regulator, String penaltyType,
                             BigDecimal amount, LocalDate occurredDate, String actor) {
        RegPenalty p = new RegPenalty(orgId, title, regulator, penaltyType, amount, occurredDate);
        RegPenalty saved = repository.save(p);
        appendLog(saved, "REG_PENALTY_CREATE", actor,
                "新建处罚约谈 title=" + title + " type=" + penaltyType + " amount=" + amount);
        return saved;
    }

    /** 开始整改：OPEN → RECTIFYING。 */
    @Transactional
    public RegPenalty rectify(Long id, String actor) {
        RegPenalty p = get(id);
        transition(p, RegPenaltyStatus.OPEN, RegPenaltyStatus.RECTIFYING);
        RegPenalty saved = repository.save(p);
        appendLog(saved, "REG_PENALTY_RECTIFY", actor, "开始整改");
        return saved;
    }

    /**
     * 了结：RECTIFYING → CLOSED（终态）。
     * M11 B13（举证门控）：了结前须在证据库挂有本处罚的整改/缴款凭证（整改完成证明、罚款缴纳回单，
     * sha256 固化）——无凭证的"办结"无法向监管举证整改到位。
     */
    @Transactional
    public RegPenalty close(Long id, String actor) {
        RegPenalty p = get(id);
        if (evidenceRepository.countByPenaltyId(id) == 0) {
            throw new IllegalStateException("了结处罚约谈前须上传整改/缴款证据（证据库关联本处罚，整改证明或缴款回单原件 sha256 固化留档）");
        }
        transition(p, RegPenaltyStatus.RECTIFYING, RegPenaltyStatus.CLOSED);
        RegPenalty saved = repository.save(p);
        appendLog(saved, "REG_PENALTY_CLOSE", actor, "了结处罚约谈");
        return saved;
    }

    private void transition(RegPenalty p, RegPenaltyStatus expectedFrom, RegPenaltyStatus to) {
        if (p.getStatus() != expectedFrom) {
            throw new IllegalStateException(
                    "非法状态流转：处罚约谈记录 id=" + p.getId()
                            + " 当前状态=" + p.getStatus()
                            + "，仅允许从 " + expectedFrom + " 流转到 " + to);
        }
        p.setStatus(to);
    }

    private void appendLog(RegPenalty p, String action, String actor, String detail) {
        hashChainService.append(p.getOrgId(), action, actor, "REG_PENALTY:" + p.getId(), detail);
    }
}
