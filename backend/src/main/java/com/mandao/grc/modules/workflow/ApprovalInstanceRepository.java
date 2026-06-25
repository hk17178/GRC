package com.mandao.grc.modules.workflow;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** 审批实例仓库。隔离由 RLS 兜底。 */
public interface ApprovalInstanceRepository extends JpaRepository<ApprovalInstance, Long> {

    /** 取某业务对象当前运行中的审批实例。 */
    Optional<ApprovalInstance> findFirstByBizTypeAndBizIdAndStatus(ApprovalBizType bizType, Long bizId, InstanceStatus status);

    /** 按 Flowable 流程实例 id 反查。 */
    Optional<ApprovalInstance> findByProcessInstanceId(String processInstanceId);
}
