package com.mandao.grc.modules.ropa;

import com.mandao.grc.modules.audit.HashChainService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 个人信息处理活动（ROPA）业务服务（M6）。
 *
 * 隔离：方法 @Transactional 且位于 com.mandao.grc.modules 包 → {@link com.mandao.grc.common.isolation.OrgScopeAspect}
 * 在事务内自动注入 app.visible_orgs，RLS 裁剪数据并校验写入（WITH CHECK，V9 已为 ropa 建）。
 *
 * 状态机：DRAFT → ACTIVE → RETIRED；非法流转抛 {@link IllegalStateException}。
 *
 * 留痕：create/activate/retire 后 {@link HashChainService#append} 写入按 org 分链的防篡改哈希链（entity="ROPA:{id}"）。
 *
 * 设计依据：需求文档 M6（个人信息处理活动 ROPA）、D1-2、D1-3 §5.1/§8、D2-5。
 */
@Service
public class RopaService {

    private final RopaRepository repository;
    private final HashChainService hashChainService;

    public RopaService(RopaRepository repository, HashChainService hashChainService) {
        this.repository = repository;
        this.hashChainService = hashChainService;
    }

    @Transactional(readOnly = true)
    public List<Ropa> list() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public Ropa get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("个人信息处理活动不存在或不可见：id=" + id));
    }

    /** 登记处理活动（DRAFT 态）。 */
    @Transactional
    public Ropa create(Long orgId, String activityName, String purpose, String dataCategories,
                       String legalBasis, boolean crossBorder, String retention, String actor) {
        Ropa r = new Ropa(orgId, activityName, purpose, dataCategories, legalBasis, crossBorder, retention);
        Ropa saved = repository.save(r);
        appendLog(saved, "ROPA_CREATE", actor,
                "登记个人信息处理活动 name=" + activityName + " legalBasis=" + legalBasis
                        + " crossBorder=" + crossBorder);
        return saved;
    }

    /** 生效：DRAFT → ACTIVE。 */
    @Transactional
    public Ropa activate(Long id, String actor) {
        Ropa r = get(id);
        transition(r, RopaStatus.DRAFT, RopaStatus.ACTIVE);
        Ropa saved = repository.save(r);
        appendLog(saved, "ROPA_ACTIVATE", actor, "处理活动生效");
        return saved;
    }

    /** 退役：ACTIVE → RETIRED（终态）。 */
    @Transactional
    public Ropa retire(Long id, String actor) {
        Ropa r = get(id);
        transition(r, RopaStatus.ACTIVE, RopaStatus.RETIRED);
        Ropa saved = repository.save(r);
        appendLog(saved, "ROPA_RETIRE", actor, "处理活动退役");
        return saved;
    }

    // ---------- 内部辅助 ----------

    private void transition(Ropa r, RopaStatus expectedFrom, RopaStatus to) {
        if (r.getStatus() != expectedFrom) {
            throw new IllegalStateException(
                    "非法状态流转：个人信息处理活动 id=" + r.getId()
                            + " 当前状态=" + r.getStatus()
                            + "，仅允许从 " + expectedFrom + " 流转到 " + to);
        }
        r.setStatus(to);
    }

    private void appendLog(Ropa r, String action, String actor, String detail) {
        hashChainService.append(r.getOrgId(), action, actor, "ROPA:" + r.getId(), detail);
    }
}
