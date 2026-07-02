package com.mandao.grc.modules.policy;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 制度版本快照仓储（RLS 裁剪）。 */
public interface PolicyVersionRepository extends JpaRepository<PolicyVersion, Long> {

    /** 某制度的版本历史（新→旧）。 */
    List<PolicyVersion> findByPolicyIdOrderByVersionNoDesc(Long policyId);
}
