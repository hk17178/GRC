package com.mandao.grc.common.isolation;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 组织隔离切面：自动为所有业务模块的事务注入会话变量 app.visible_orgs。
 *
 * 作用：替代"每个 Service 方法手动调用 OrgScopeApplier.apply()"的写法——
 * 凡 {@code com.mandao.grc.modules} 包下被 {@code @Transactional} 标注的方法（类级或方法级），
 * 进入事务后由本切面统一注入隔离上下文，使新增模块"零成本"自动受隔离保护，
 * 不依赖开发者每次记得手写。这是"统一数据访问层"的落地（D1-3 §5.1、D1-8 §四）。
 *
 * 顺序：@Order(10) 必须大于事务通知的 order(0，见 {@link IsolationConfig})，
 * 确保本切面在事务开启之后、查询之前执行，SET LOCAL 与查询同连接。
 */
@Aspect
@Component
@Order(10)
public class OrgScopeAspect {

    private final OrgScopeApplier orgScopeApplier;

    public OrgScopeAspect(OrgScopeApplier orgScopeApplier) {
        this.orgScopeApplier = orgScopeApplier;
    }

    /**
     * 切入点：modules 包下、带 @Transactional（方法级或类级）的方法。
     * @Before 在事务通知之后执行（因本切面 order 更大、更内层），故已处于活动事务中。
     */
    @Before("(@annotation(org.springframework.transaction.annotation.Transactional) "
            + "|| @within(org.springframework.transaction.annotation.Transactional)) "
            + "&& within(com.mandao.grc.modules..*)")
    public void injectOrgScope() {
        orgScopeApplier.apply();
    }
}
