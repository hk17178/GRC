package com.mandao.grc.modules.audit.management;

import com.mandao.grc.modules.audit.HashChainService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 整改工单业务服务（M3：发现→整改→验证闭环）。
 *
 * 隔离/留痕范式同其它模块。工单状态机 PENDING→IN_PROGRESS→SUBMITTED→VERIFIED；
 * SUBMITTED 验证不通过退回 IN_PROGRESS。非法流转一律抛 {@link IllegalStateException}。
 *
 * 与 {@link AuditFindingService#remediate} 衔接：发现须有 ≥1 条 VERIFIED 工单方可标记已整改（验证闭环红线）。
 *
 * 设计依据：需求 M3 审计管理（整改跟踪/验证闭环）、D1-2、D2-5。
 */
@Service
public class RemediationService {

    private final RemediationOrderRepository orderRepository;
    private final AuditFindingService findingService;
    private final HashChainService hashChainService;

    /** 证据仓库（七轮 7-1：提交门控需核验证据库挂件，setter 注入避免搅动既有构造装配）。 */
    private EvidenceRepository evidenceRepository;

    @org.springframework.beans.factory.annotation.Autowired
    void wireEvidenceRepository(EvidenceRepository evidenceRepository) {
        this.evidenceRepository = evidenceRepository;
    }

    /** 账号库（M-3：职责分离比对需把 actor 解析到登录账号的用户名/显示名，setter 注入不搅动构造）。 */
    private com.mandao.grc.common.auth.AppUserRepository appUserRepository;

    @org.springframework.beans.factory.annotation.Autowired
    void wireAppUserRepository(com.mandao.grc.common.auth.AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    public RemediationService(RemediationOrderRepository orderRepository,
                              AuditFindingService findingService,
                              HashChainService hashChainService) {
        this.orderRepository = orderRepository;
        this.findingService = findingService;
        this.hashChainService = hashChainService;
    }

    @Transactional(readOnly = true)
    public List<RemediationOrder> listByFinding(Long findingId) {
        return orderRepository.findByFindingId(findingId);
    }

    /** 按审计类型跨发现列整改工单（外审页"整改跟踪"汇总视图）。 */
    @Transactional(readOnly = true)
    public List<RemediationOrder> listByType(AuditType type) {
        return orderRepository.findByPlanType(type);
    }

    @Transactional(readOnly = true)
    public RemediationOrder get(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("整改工单不存在或不可见：id=" + id));
    }

    /**
     * 派单：为某审计发现新建一条整改工单（PENDING）。
     * 经 {@link AuditFindingService#get} 校验发现可见（并取其所属组织），桥接发现与工单同组织。
     */
    @Transactional
    public RemediationOrder create(Long findingId, String assignee, LocalDate dueDate, String measure, String actor) {
        AuditFinding f = findingService.get(findingId); // 可见性校验 + 取 orgId
        RemediationOrder order = new RemediationOrder(f.getOrgId(), findingId, assignee, dueDate, measure);
        RemediationOrder saved = orderRepository.save(order);
        hashChainService.append(f.getOrgId(), "REMEDIATION_CREATE", actor, "REMEDIATION:" + saved.getId(),
                "派整改工单 finding=" + findingId + " assignee=" + assignee + " due=" + dueDate);
        return saved;
    }

    /** 开始整改：PENDING → IN_PROGRESS。 */
    @Transactional
    public RemediationOrder start(Long id, String actor) {
        RemediationOrder o = get(id);
        requireStatus(o, RemediationStatus.PENDING, "开始整改");
        o.start();
        return saveAndLog(o, "REMEDIATION_START", actor, "开始整改");
    }

    /**
     * 提交整改：IN_PROGRESS → SUBMITTED。
     * 七轮 7-1（B1 红线）：整改闭环必须携证明材料——证据说明必填，且须在证据库挂有
     * 关联本工单的证据文件（sha256 固化），两者缺一不可提交。
     */
    @Transactional
    public RemediationOrder submit(Long id, String evidence, String actor) {
        RemediationOrder o = get(id);
        requireStatus(o, RemediationStatus.IN_PROGRESS, "提交整改");
        if (evidence == null || evidence.isBlank()) {
            throw new IllegalStateException("整改提交必须填写证据说明（UAT§6 红线：整改闭环须携证明材料）");
        }
        if (evidenceRepository.countByRemediationId(id) == 0) {
            throw new IllegalStateException("整改提交前须在证据库上传至少一份关联本工单的证明材料（原件 sha256 固化留档）");
        }
        o.submit(evidence);
        return saveAndLog(o, "REMEDIATION_SUBMIT", actor, "提交整改证据");
    }

    /**
     * 验证通过：SUBMITTED → VERIFIED。
     * 七轮 7-1（B1 红线）：职责分离——整改责任人不得自行验证自己的整改。
     */
    @Transactional
    public RemediationOrder verify(Long id, String actor) {
        RemediationOrder o = get(id);
        requireStatus(o, RemediationStatus.SUBMITTED, "验证");
        if (isSameParty(actor, o.getAssignee())) {   // M-3：规范化 + 显示名比对，防命名不一致绕过
            throw new IllegalStateException("职责分离：整改责任人不得自行验证自己的整改，请由下达人或独立验证人执行");
        }
        o.verify(actor);
        return saveAndLog(o, "REMEDIATION_VERIFY", actor, "验证通过");
    }

    /**
     * M-3：判定验证人是否即整改责任人。规范化(trim/忽略大小写)比对 actor 与 assignee，
     * 并把 actor 解析到其登录账号的 用户名/显示名 一并比对——堵住"assignee 存显示名、actor 是用户名"
     * 或大小写/空白不一致导致的自验自改绕过。
     */
    private boolean isSameParty(String actor, String assignee) {
        if (actor == null || assignee == null || assignee.isBlank()) {
            return false;
        }
        String a = assignee.trim();
        if (a.equalsIgnoreCase(actor.trim())) {
            return true;
        }
        if (appUserRepository != null) {
            com.mandao.grc.common.auth.AppUser u = appUserRepository.findByUsername(actor.trim()).orElse(null);
            if (u != null && u.getDisplayName() != null && a.equalsIgnoreCase(u.getDisplayName().trim())) {
                return true;
            }
        }
        return false;
    }

    /** 验证不通过：SUBMITTED → IN_PROGRESS（退回返工）。 */
    @Transactional
    public RemediationOrder reject(Long id, String reason, String actor) {
        RemediationOrder o = get(id);
        requireStatus(o, RemediationStatus.SUBMITTED, "退回");
        o.reject();
        return saveAndLog(o, "REMEDIATION_REJECT", actor, "验证不通过退回：" + (reason == null ? "" : reason));
    }

    // ---------- 内部辅助 ----------

    private void requireStatus(RemediationOrder o, RemediationStatus expected, String op) {
        if (o.getStatus() != expected) {
            throw new IllegalStateException(
                    "非法整改流转：工单 id=" + o.getId() + " 当前状态=" + o.getStatus()
                            + "，操作[" + op + "]仅允许在 " + expected + " 态");
        }
    }

    private RemediationOrder saveAndLog(RemediationOrder o, String action, String actor, String detail) {
        RemediationOrder saved = orderRepository.save(o);
        hashChainService.append(o.getOrgId(), action, actor, "REMEDIATION:" + o.getId(), detail);
        return saved;
    }
}
