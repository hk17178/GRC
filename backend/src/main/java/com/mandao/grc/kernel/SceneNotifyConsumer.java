package com.mandao.grc.kernel;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 自定义通知场景内核消费（D1-8 §九 接线二）。
 *
 * 通知规则引擎产出一条新告警后调用本消费：按 (org, 事件类型) 找出该组织已装配、且涵盖此事件的
 * 运行态场景（notification_scene ← notif_scene_def），为每个命中场景生成一条 scene_notification
 * （承接接收角色快照 + 消息 + 单据引用），供升级链运行器后续按延迟逐级升级。
 *
 * 为何在内核而非调用模块层 assemble()：模块层 assemble 是 modules 包 @Transactional，
 * 会被 OrgScopeAspect 用 IsolationContext 注入 visible_orgs——而内核无请求上下文（用 setAllOrgs），
 * 切面会把 visible_orgs 置为 '-1' 致 RLS 全拒。故内核在自己的 setAllOrgs 会话下走原生查询，
 * 并【显式按 org_id 过滤】：跨子公司永不外溢（A 组织告警绝不落到 B 组织场景）。
 *
 * 调用方（{@link NotifyRuleEngine#dispatch}）已在事务内且已 setAllOrgs，本方法不另开事务。
 */
@Service
public class SceneNotifyConsumer {

    @PersistenceContext
    private EntityManager em;

    /**
     * 消费一条告警：为本组织命中该事件的每个启用场景生成一条 scene_notification（幂等）。
     *
     * @return 本次真正新增的场景通知条数（幂等冲突不计）
     */
    public int consume(long orgId, String eventType, String objectType, long objectId, String message) {
        // 命中该事件类型的本组织启用场景。event_types 为 JSON 文本（如 ["RULE_KRI_BREACH"]），
        // 用带引号的 token 精确子串匹配（position），避免 jsonb ? 运算符与 JDBC 占位冲突、及 token 前缀误命中。
        @SuppressWarnings("unchecked")
        List<Object[]> scenes = em.createNativeQuery(
                        "SELECT s.id, s.recipient_roles FROM notification_scene s "
                                + "JOIN notif_scene_def d ON d.id = s.scene_def_id "
                                + "WHERE s.org_id = :org AND s.status = 'ACTIVE' "
                                + "AND strpos(d.event_types, :tok) > 0")
                .setParameter("org", orgId)
                .setParameter("tok", "\"" + eventType + "\"")
                .getResultList();

        int produced = 0;
        for (Object[] s : scenes) {
            long sceneId = ((Number) s[0]).longValue();
            String roles = (String) s[1];
            int inserted = em.createNativeQuery(
                            "INSERT INTO scene_notification"
                                    + "(org_id, scene_id, event_type, object_type, object_id, message, recipient_roles) "
                                    + "VALUES (:org, :sid, :et, :ot, :oid, :msg, :roles) "
                                    + "ON CONFLICT (scene_id, object_type, object_id, event_type) DO NOTHING")
                    .setParameter("org", orgId)
                    .setParameter("sid", sceneId)
                    .setParameter("et", eventType)
                    .setParameter("ot", objectType)
                    .setParameter("oid", objectId)
                    .setParameter("msg", message)
                    .setParameter("roles", roles)
                    .executeUpdate();
            produced += inserted;
        }
        return produced;
    }

    /**
     * 确认某单据的场景通知（处理即止链）：把该 (org, 单据) 下 PENDING 的场景通知置 ACKED，
     * 升级链运行器不再对其升级。返回被确认的条数。业务方处理告警对应单据时调用。
     *
     * 内核方法（不受隔离切面）：自设 setAllOrgs 后【显式按 org_id 过滤】——只确认目标组织的通知，
     * 绝不误确认他组织的。故置全可见仅为让本 org 的行可见，实际写入仍锁定单一 org。
     */
    @Transactional
    public int acknowledge(long orgId, String objectType, long objectId) {
        VisibleOrgsSql.setAllOrgs(em);
        return em.createNativeQuery(
                        "UPDATE scene_notification SET status = 'ACKED', acked_at = now() "
                                + "WHERE org_id = :org AND object_type = :ot AND object_id = :oid AND status = 'PENDING'")
                .setParameter("org", orgId)
                .setParameter("ot", objectType)
                .setParameter("oid", objectId)
                .executeUpdate();
    }
}
