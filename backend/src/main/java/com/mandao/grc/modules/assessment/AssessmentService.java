package com.mandao.grc.modules.assessment;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 风险评估业务服务。
 *
 * 注意：本服务【不再手动注入隔离上下文】——只要方法带 @Transactional 且位于 modules 包，
 * {@link com.mandao.grc.common.isolation.OrgScopeAspect} 会在事务内自动注入 app.visible_orgs，
 * 随后 RLS 依据其裁剪数据。业务代码因此只关注业务，隔离由平台统一保证。
 */
@Service
public class AssessmentService {

    private final AssessmentRepository repository;

    public AssessmentService(AssessmentRepository repository) {
        this.repository = repository;
    }

    /**
     * 列出当前主体可见组织范围内的风险评估。
     * findAll() 不带任何 org 过滤——隔离完全由切面注入 + RLS 兜底保证。
     */
    @Transactional(readOnly = true)
    public List<Assessment> list() {
        return repository.findAll();
    }
}
