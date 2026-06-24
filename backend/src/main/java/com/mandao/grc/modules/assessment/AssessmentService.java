package com.mandao.grc.modules.assessment;

import com.mandao.grc.common.isolation.OrgScopeApplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AssessmentService {

    private final AssessmentRepository repository;
    private final OrgScopeApplier orgScopeApplier;

    public AssessmentService(AssessmentRepository repository, OrgScopeApplier orgScopeApplier) {
        this.repository = repository;
        this.orgScopeApplier = orgScopeApplier;
    }

    /**
     * 关键顺序：先在同一事务注入 app.visible_orgs，再查询；RLS 据此裁剪。
     * 统一在服务层调用 orgScopeApplier.apply()，是"统一数据访问层"的最小实现，
     * 后续可上移为 AOP 切面统一拦截 @Transactional（见 D1-8 §四、D1-9 H-05）。
     */
    @Transactional(readOnly = true)
    public List<Assessment> list() {
        orgScopeApplier.apply();
        return repository.findAll();
    }
}
