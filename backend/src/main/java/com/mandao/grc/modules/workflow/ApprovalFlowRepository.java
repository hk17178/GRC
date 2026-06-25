package com.mandao.grc.modules.workflow;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 审批流定义仓库。隔离由 RLS 兜底（查询不手写 org 过滤）。
 */
public interface ApprovalFlowRepository extends JpaRepository<ApprovalFlow, Long> {

    /** 列出某业务类型的全部流程（含草稿/历史），最新在前。 */
    List<ApprovalFlow> findByBizTypeOrderByIdDesc(ApprovalBizType bizType);

    /** 列出可见范围内的全部流程，最新在前。 */
    List<ApprovalFlow> findAllByOrderByIdDesc();

    /** 取某业务类型当前生效的流程（用于发起审批时选流程）。 */
    Optional<ApprovalFlow> findFirstByBizTypeAndStatus(ApprovalBizType bizType, FlowStatus status);
}
