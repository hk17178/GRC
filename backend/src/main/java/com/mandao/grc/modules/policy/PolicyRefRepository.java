package com.mandao.grc.modules.policy;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 制度引用关系仓储（RLS 裁剪）。 */
public interface PolicyRefRepository extends JpaRepository<PolicyRef, Long> {

    /** 某制度引用了哪些制度。 */
    List<PolicyRef> findByPolicyId(Long policyId);

    /** 哪些制度引用了某制度（反向）。 */
    List<PolicyRef> findByRefPolicyId(Long refPolicyId);
}
