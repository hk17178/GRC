package com.mandao.grc.common.isolation;

import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 隔离相关的 AOP / 事务编排配置。
 *
 * 关键：将事务通知（TransactionInterceptor）的优先级设为最高（order=0，最外层），
 * 从而保证 {@link OrgScopeAspect}（order=10，较内层）在【事务已开启之后】才执行
 * SET LOCAL app.visible_orgs —— 必须与后续查询处于同一事务、同一数据库连接，
 * RLS 才能正确依据该会话变量裁剪数据。
 *
 * 通知链（调用 modules 包下 @Transactional 方法时）：
 *   事务开启(order0) → OrgScopeAspect 注入 visible_orgs(order10) → 业务方法 → 事务提交
 */
@Configuration
@EnableTransactionManagement(order = 0)
public class IsolationConfig {
}
