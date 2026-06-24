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

    /** 了结：RECTIFYING → CLOSED（终态）。 */
    @Transactional
    public RegPenalty close(Long id, String actor) {
        RegPenalty p = get(id);
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
