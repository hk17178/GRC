package com.mandao.grc.modules.permission;

import com.mandao.grc.modules.audit.HashChainService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * UAR 权限审阅业务服务（M8 权限审批；落地"权限审阅"流程）。
 *
 * 隔离/留痕范式同 {@link PermissionService}：方法 @Transactional → 切面自动注入 visible_orgs，
 * RLS 裁剪 + WITH CHECK 校验；每步流转/决定调用 {@link HashChainService#append} 留痕。
 *
 * 审阅状态机：OPEN → IN_REVIEW → COMPLETED（非法流转抛 {@link IllegalStateException}）。
 * 仅 IN_REVIEW 态可对逐项做决定；decision=REVOKE 联动把对应 {@link UserRoleOrg} 置 active=false（撤销授权）。
 *
 * 典型流程：createReview → startReview（快照该 org 当前全部有效授权为审阅项）
 *           → decideItem(KEEP/REVOKE) ... → completeReview。
 *
 * 设计依据：需求文档 M8 权限审批（UAR）、D1-3 §4.7、D2-5。
 */
@Service
public class AccessReviewService {

    private final AccessReviewRepository reviewRepository;
    private final AccessReviewItemRepository itemRepository;
    private final UserRoleOrgRepository userRoleOrgRepository;
    private final HashChainService hashChainService;

    public AccessReviewService(AccessReviewRepository reviewRepository,
                               AccessReviewItemRepository itemRepository,
                               UserRoleOrgRepository userRoleOrgRepository,
                               HashChainService hashChainService) {
        this.reviewRepository = reviewRepository;
        this.itemRepository = itemRepository;
        this.userRoleOrgRepository = userRoleOrgRepository;
        this.hashChainService = hashChainService;
    }

    /** 按 id 取审阅（不可见则视为不存在）。 */
    @Transactional(readOnly = true)
    public AccessReview get(Long id) {
        return reviewRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("权限审阅不存在或不可见：id=" + id));
    }

    /** 列出某审阅下的全部审阅项（受 RLS 裁剪）。 */
    @Transactional(readOnly = true)
    public List<AccessReviewItem> listItems(Long reviewId) {
        return itemRepository.findByAccessReviewId(reviewId);
    }

    /** 新建权限审阅（OPEN 态）。 */
    @Transactional
    public AccessReview createReview(Long orgId, String period, String reviewer, String actor) {
        AccessReview saved = reviewRepository.save(new AccessReview(orgId, period, reviewer));
        appendLog(orgId, "ACCESS_REVIEW_CREATE", actor, "ACCESS_REVIEW:" + saved.getId(),
                "新建权限审阅 period=" + period + " reviewer=" + reviewer);
        return saved;
    }

    /**
     * 开始审阅：OPEN → IN_REVIEW，并把该 org 当前全部【有效】授权四元组快照为待决审阅项（PENDING）。
     */
    @Transactional
    public AccessReview startReview(Long id, String actor) {
        AccessReview r = get(id);
        transition(r, AccessReviewStatus.OPEN, AccessReviewStatus.IN_REVIEW);
        AccessReview saved = reviewRepository.save(r);

        // 快照当前有效授权为审阅项
        List<UserRoleOrg> active = userRoleOrgRepository.findByOrgIdAndActiveTrue(r.getOrgId());
        for (UserRoleOrg uro : active) {
            itemRepository.save(new AccessReviewItem(r.getOrgId(), r.getId(), uro.getId()));
        }

        appendLog(r.getOrgId(), "ACCESS_REVIEW_START", actor, "ACCESS_REVIEW:" + r.getId(),
                "开始权限审阅，快照有效授权 " + active.size() + " 项");
        return saved;
    }

    /**
     * 对某审阅项做决定（KEEP/REVOKE）。仅审阅处于 IN_REVIEW 态允许。
     * REVOKE 时联动把对应 {@link UserRoleOrg} 置 active=false（撤销授权）。
     *
     * @param decision 必须是 KEEP 或 REVOKE（PENDING 非法）
     */
    @Transactional
    public AccessReviewItem decideItem(Long itemId, AccessReviewDecision decision, String actor) {
        if (decision == null || decision == AccessReviewDecision.PENDING) {
            throw new IllegalArgumentException("审阅决定必须为 KEEP 或 REVOKE，收到：" + decision);
        }
        AccessReviewItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("审阅项不存在或不可见：id=" + itemId));
        AccessReview r = get(item.getAccessReviewId());
        if (r.getStatus() != AccessReviewStatus.IN_REVIEW) {
            throw new IllegalStateException(
                    "仅审阅中(IN_REVIEW)可做决定；审阅 id=" + r.getId() + " 当前状态=" + r.getStatus());
        }

        item.setDecision(decision);
        item.setReviewedAt(OffsetDateTime.now());
        AccessReviewItem savedItem = itemRepository.save(item);

        if (decision == AccessReviewDecision.REVOKE) {
            UserRoleOrg uro = userRoleOrgRepository.findById(item.getUserRoleOrgId())
                    .orElseThrow(() -> new IllegalStateException(
                            "审阅项对应授权不存在或不可见：userRoleOrgId=" + item.getUserRoleOrgId()));
            if (uro.isActive()) {
                uro.setActive(false);
                userRoleOrgRepository.save(uro);
            }
            appendLog(r.getOrgId(), "ACCESS_REVIEW_REVOKE", actor, "USER_ROLE_ORG:" + uro.getId(),
                    "审阅撤销授权 reviewItem=" + itemId + " userRoleOrg=" + uro.getId());
        } else {
            appendLog(r.getOrgId(), "ACCESS_REVIEW_KEEP", actor, "ACCESS_REVIEW_ITEM:" + itemId,
                    "审阅保留授权 reviewItem=" + itemId + " userRoleOrg=" + item.getUserRoleOrgId());
        }
        return savedItem;
    }

    /** 完成审阅：IN_REVIEW → COMPLETED（终态）。 */
    @Transactional
    public AccessReview completeReview(Long id, String actor) {
        AccessReview r = get(id);
        transition(r, AccessReviewStatus.IN_REVIEW, AccessReviewStatus.COMPLETED);
        AccessReview saved = reviewRepository.save(r);
        appendLog(r.getOrgId(), "ACCESS_REVIEW_COMPLETE", actor, "ACCESS_REVIEW:" + r.getId(),
                "完成权限审阅");
        return saved;
    }

    // ---------- 内部辅助 ----------

    /** 校验并执行一次合法流转：当前态须 == expectedFrom，否则视为非法流转抛异常。 */
    private void transition(AccessReview r, AccessReviewStatus expectedFrom, AccessReviewStatus to) {
        if (r.getStatus() != expectedFrom) {
            throw new IllegalStateException(
                    "非法状态流转：权限审阅 id=" + r.getId()
                            + " 当前状态=" + r.getStatus()
                            + "，仅允许从 " + expectedFrom + " 流转到 " + to);
        }
        r.setStatus(to);
    }

    /** 统一留痕入口。 */
    private void appendLog(Long orgId, String action, String actor, String entity, String detail) {
        hashChainService.append(orgId, action, actor, entity, detail);
    }
}
